import pandas as pd
import numpy as np
import pickle
from sklearn.preprocessing import MinMaxScaler, LabelEncoder
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
import matplotlib.pyplot as plt

from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Conv1D, GRU, Dense, Dropout
from tensorflow.keras.losses import Huber
from tensorflow.keras.callbacks import EarlyStopping

# ==============================================
# 1️⃣ Load Bursty Dataset
# ==============================================
df = pd.read_csv("../data/cloud_workload_diurnal.csv")
df["Task_Start_Time"] = pd.to_datetime(df["Task_Start_Time"])

# ==============================================
# 2️⃣ Feature Engineering
# ==============================================
df["Hour"] = df["Task_Start_Time"].dt.hour
df["Day"] = df["Task_Start_Time"].dt.day
df["Month"] = df["Task_Start_Time"].dt.month

# Short lags → better for bursty unpredictable spikes
df["Prev_Throughput_1"] = df["System_Throughput (tasks/sec)"].shift(1)
df["Prev_Throughput_2"] = df["System_Throughput (tasks/sec)"].shift(2)
df["Prev_Throughput_3"] = df["System_Throughput (tasks/sec)"].shift(3)
df.dropna(inplace=True)

# Encode Job Priority
if df["Job_Priority"].dtype == "object":
    le = LabelEncoder()
    df["Job_Priority"] = le.fit_transform(df["Job_Priority"])
    with open("models/job_priority_encoder.pkl", "wb") as f:
        pickle.dump(le, f)

# ==============================================
# 3️⃣ Select Input Features and Target
# ==============================================
features = [
    "Job_Priority", "CPU_Utilization (%)", "Memory_Consumption (MB)",
    "Hour", "Day", "Month",
    "Prev_Throughput_1", "Prev_Throughput_2", "Prev_Throughput_3"
]
target = "System_Throughput (tasks/sec)"

X = df[features].values
y = df[target].values.reshape(-1, 1)

# ==============================================
# 4️⃣ Scaling
# ==============================================
scaler = MinMaxScaler()
X_scaled = scaler.fit_transform(X)
y_scaled = scaler.fit_transform(y)

with open("models/scaler.pkl", "wb") as f:
    pickle.dump(scaler, f)

# ==============================================
# 5️⃣ Sequence Preparation for CNN+GRU
# ==============================================
seq_len = 10  # Small window works better for spikes

X_seq, y_seq = [], []
for i in range(seq_len, len(X_scaled)):
    X_seq.append(X_scaled[i - seq_len:i])
    y_seq.append(y_scaled[i])

X_seq, y_seq = np.array(X_seq), np.array(y_seq)

split_idx = int(0.8 * len(X_seq))
X_train, X_test = X_seq[:split_idx], X_seq[split_idx:]
y_train, y_test = y_seq[:split_idx], y_seq[split_idx:]

# ==============================================
# 6️⃣ CNN + GRU Hybrid Model (Best for Bursty)
# ==============================================
model = Sequential([

    # CNN detects sudden spikes & local features
    Conv1D(filters=64, kernel_size=3, padding="same", activation="relu",
           input_shape=(X_train.shape[1], X_train.shape[2])),
    Dropout(0.3),

    # GRU learns sequence behavior
    GRU(128, return_sequences=True, activation="tanh"),
    Dropout(0.3),
    GRU(64, activation="tanh"),
    Dropout(0.3),

    Dense(32, activation="relu"),
    Dense(1)
])

model.compile(optimizer="adam", loss=Huber())

early_stop = EarlyStopping(monitor="val_loss", patience=15, restore_best_weights=True)

# ==============================================
# 7️⃣ Train Model
# ==============================================
history = model.fit(
    X_train, y_train,
    validation_data=(X_test, y_test),
    epochs=120,
    batch_size=32,
    callbacks=[early_stop],
    verbose=1
)

model.save("models/cnn_gru_bursty_predictor.keras")

# ==============================================
# 8️⃣ Predict and Inverse Transform
# ==============================================
y_pred = model.predict(X_test)
y_pred_inv = scaler.inverse_transform(y_pred)
y_test_inv = scaler.inverse_transform(y_test)

y_pred_full = model.predict(X_seq)
y_pred_full_inv = scaler.inverse_transform(y_pred_full)

df_result = df.iloc[seq_len:].copy()
df_result["Predicted_Throughput (tasks/sec)"] = y_pred_full_inv
df_result.to_csv("../results/cnn_gru_bursty_predictions.csv", index=False)

# Metrics
mae = mean_absolute_error(y_test_inv, y_pred_inv)
rmse = np.sqrt(mean_squared_error(y_test_inv, y_pred_inv))
r2 = r2_score(y_test_inv, y_pred_inv)

print(f"📊 MAE: {mae:.3f}")
print(f"📊 RMSE: {rmse:.3f}")
print(f"📈 R² Score: {r2:.3f}")

# ==============================================
# 9️⃣ Plot Results
# ==============================================
plt.figure(figsize=(11,5))
plt.plot(y_test_inv, label="Actual Throughput", color="blue")
plt.plot(y_pred_inv, label="Predicted Throughput (CNN+GRU)", color="red", linestyle="--")
plt.xlabel("Time Steps")
plt.ylabel("System Throughput (tasks/sec)")
plt.title("CNN + GRU Hybrid Model: Actual vs Predicted Bursty Throughput")
plt.legend()
plt.tight_layout()
plt.savefig("../results/cnn_gru_bursty_plot.png")
plt.show()
