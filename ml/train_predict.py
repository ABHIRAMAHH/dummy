# train_predict.py
# import pandas as pd
# import numpy as np
# from sklearn.preprocessing import MinMaxScaler
# from tensorflow.keras.models import Sequential
# from tensorflow.keras.layers import LSTM, Dense, Dropout
# import joblib
# import os
#
# # === CONFIG ===
# DATA_PATH = "../data/cloud_workload_steady.csv"   # <-- update if different
# MODEL_PATH = "models/lstm_predictor.keras"
# SCALER_PATH = "models/scaler.pkl"
#
# # === LOAD DATA ===
# df = pd.read_csv(DATA_PATH)
# df['Task_Start_Time'] = pd.to_datetime(df['Task_Start_Time'], errors='coerce')
#
# # Select numerical features for prediction
# features = [
#     'CPU_Utilization (%)',
#     'Memory_Consumption (MB)',
#     'Task_Execution_Time (ms)',
#     'Number_of_Active_Users',
#     'Network_Bandwidth_Utilization (Mbps)',
#     'Job_Priority'
# ]
#
# # Encode 'Job_Priority' to numeric (Low, Medium, High)
# df['Job_Priority'] = df['Job_Priority'].map({'Low': 0, 'Medium': 1, 'High': 2})
#
# # Target: System Throughput
# target = 'System_Throughput (tasks/sec)'
#
# # Drop any missing rows
# df = df.dropna(subset=features + [target])
#
# # === SCALE DATA ===
# scaler = MinMaxScaler()
# scaled_data = scaler.fit_transform(df[features + [target]])
# joblib.dump(scaler, SCALER_PATH)
#
# X_scaled = scaled_data[:, :-1]
# y_scaled = scaled_data[:, -1]
#
# # === CREATE SEQUENCES ===
# seq_len = 10
# X, y = [], []
# for i in range(seq_len, len(X_scaled)):
#     X.append(X_scaled[i-seq_len:i])
#     y.append(y_scaled[i])
# X, y = np.array(X), np.array(y)
#
# # === BUILD LSTM MODEL ===
# model = Sequential([
#     LSTM(64, return_sequences=True, input_shape=(X.shape[1], X.shape[2])),
#     Dropout(0.2),
#     LSTM(32),
#     Dense(1)
# ])
#
# model.compile(optimizer='adam', loss='mse')
# model.fit(X, y, epochs=30, batch_size=32, validation_split=0.2)
#
# # === SAVE MODEL ===
# model.save(MODEL_PATH)
# print(f"✅ Model trained and saved to {MODEL_PATH}")
# print(f"✅ Scaler saved to {SCALER_PATH}")




import pandas as pd
import numpy as np
import pickle
from sklearn.preprocessing import MinMaxScaler, LabelEncoder
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
import matplotlib.pyplot as plt
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense, Dropout
from tensorflow.keras.callbacks import EarlyStopping

# ==============================================
# 1️⃣ Load Dataset
# ==============================================
df = pd.read_csv("../data/cloud_workload_diurnal.csv")
df["Task_Start_Time"] = pd.to_datetime(df["Task_Start_Time"])

# ==============================================
# 2️⃣ Feature Engineering (Time-based + Lag)
# ==============================================
df["Hour"] = df["Task_Start_Time"].dt.hour
df["Day"] = df["Task_Start_Time"].dt.day
df["Month"] = df["Task_Start_Time"].dt.month

# Add lag features
df["Prev_Throughput_1"] = df["System_Throughput (tasks/sec)"].shift(1)
df["Prev_Throughput_2"] = df["System_Throughput (tasks/sec)"].shift(2)
df.dropna(inplace=True)

# Encode categorical feature
if df["Job_Priority"].dtype == "object":
    le = LabelEncoder()
    df["Job_Priority"] = le.fit_transform(df["Job_Priority"])
    # Save encoder for inference
    with open("models/job_priority_encoder.pkl", "wb") as f:
        pickle.dump(le, f)

# ==============================================
# 3️⃣ Select features and target
# ==============================================
features = ["Job_Priority", "CPU_Utilization (%)", "Memory_Consumption (MB)",
            "Hour", "Day", "Month", "Prev_Throughput_1", "Prev_Throughput_2"]
target = "System_Throughput (tasks/sec)"

X = df[features].values
y = df[target].values.reshape(-1, 1)

# ==============================================
# 4️⃣ Normalize Data
# ==============================================
scaler = MinMaxScaler()
X_scaled = scaler.fit_transform(X)
y_scaled = scaler.fit_transform(y)

# Save scaler
with open("models/scaler.pkl", "wb") as f:
    pickle.dump(scaler, f)

# ==============================================
# 5️⃣ Sequence Preparation (LSTM input)
# ==============================================
seq_len = 20
X_seq, y_seq = [], []
for i in range(seq_len, len(X_scaled)):
    X_seq.append(X_scaled[i - seq_len:i])
    y_seq.append(y_scaled[i])
X_seq, y_seq = np.array(X_seq), np.array(y_seq)

split = int(0.8 * len(X_seq))
X_train, X_test = X_seq[:split], X_seq[split:]
y_train, y_test = y_seq[:split], y_seq[split:]

# ==============================================
# 6️⃣ LSTM Model
# ==============================================
model = Sequential([
    LSTM(128, activation="tanh", return_sequences=True, input_shape=(X_train.shape[1], X_train.shape[2])),
    Dropout(0.3),
    LSTM(64, activation="tanh"),
    Dropout(0.3),
    Dense(32, activation="relu"),
    Dense(1)
])

model.compile(optimizer="adam", loss="mse")

# Early stopping
early_stop = EarlyStopping(monitor="val_loss", patience=10, restore_best_weights=True)

# ==============================================
# 7️⃣ Train Model
# ==============================================
history = model.fit(
    X_train, y_train,
    validation_data=(X_test, y_test),
    epochs=80,
    batch_size=16,
    callbacks=[early_stop],
    verbose=1
)

# Save model
model.save("models/lstm_predictor.keras")

# ==============================================
# 8️⃣ Evaluation
# ==============================================
y_pred = model.predict(X_test)
y_pred_inv = scaler.inverse_transform(y_pred)
y_test_inv = scaler.inverse_transform(y_test)

y_pred_full = model.predict(X_seq)
y_pred_full_inv = scaler.inverse_transform(y_pred_full)
df_result = df.iloc[seq_len:].copy()
df_result["Predicted_Throughput (tasks/sec)"] = y_pred_full_inv

# Save as a new CSV file
output_path = "../results/cloud_workload_with_predictions.csv"
df_result.to_csv(output_path, index=False)
mae = mean_absolute_error(y_test_inv, y_pred_inv)
rmse = np.sqrt(mean_squared_error(y_test_inv, y_pred_inv))
r2 = r2_score(y_test_inv, y_pred_inv)

print(f"📊 MAE: {mae:.2f}")
print(f"📊 RMSE: {rmse:.2f}")
print(f"📈 R² Score: {r2:.3f}")

# ==============================================
# 9️⃣ Plot Results
# ==============================================
plt.figure(figsize=(10,5))
plt.plot(y_test_inv, label="Actual Throughput", color="blue")
plt.plot(y_pred_inv, label="Predicted Throughput", color="orange", linestyle="--")
plt.xlabel("Time Steps")
plt.ylabel("System Throughput (tasks/sec)")
plt.title("Improved Model: Actual vs Predicted System Throughput")
plt.legend()
plt.tight_layout()
plt.savefig("../results/throughput_prediction_improved.png")
plt.show()
