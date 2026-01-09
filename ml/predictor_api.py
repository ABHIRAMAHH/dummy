
import pandas as pd
import numpy as np
import joblib
from tensorflow.keras.models import load_model
import os
from datetime import datetime

# Paths
MODEL_PATH = "./models/lstm_predictor.keras"
SCALER_PATH = "./models/scaler.pkl"
INPUT_CSV = "../data/cloud_workload_steady.csv"       # 🔹 your input file (update path as needed)
OUTPUT_DIR = "../results"                    # 🔹 output directory for results

# Create output directory if not exists
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Load model and scaler
model = load_model(MODEL_PATH)
scaler = joblib.load(SCALER_PATH)

# Load input dataset
df = pd.read_csv(INPUT_CSV)

# Encode Job_Priority
if "Job_Priority" in df.columns:
    df["Job_Priority"] = df["Job_Priority"].map({"Low": 0, "Medium": 1, "High": 2}).fillna(1)

# Define feature columns
feature_cols = [
    "CPU_Utilization (%)",
    "Memory_Consumption (MB)",
    "Task_Execution_Time (ms)",
    "Number_of_Active_Users",
    "Network_Bandwidth_Utilization (Mbps)",
    "Job_Priority"
]

# Check required columns
missing = [col for col in feature_cols + ["System_Throughput (tasks/sec)"] if col not in df.columns]
if missing:
    raise ValueError(f"❌ Missing columns in input CSV: {missing}")

# Scale features
scaled_features = scaler.transform(df[feature_cols + ["System_Throughput (tasks/sec)"]])

# Predict for all rows
predictions = []
for i in range(len(df)):
    X_input = np.expand_dims(scaled_features[i, :-1], axis=(0, 1))
    pred_scaled = model.predict(X_input, verbose=0)
    prediction = scaler.inverse_transform(
        np.concatenate([scaled_features[i:i+1, :-1], pred_scaled], axis=1)
    )[:, -1][0]
    predictions.append(prediction)

# Save all predictions
df["Predicted_Throughput (tasks/sec)"] = predictions

timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
filename = os.path.join(OUTPUT_DIR, f"predicted_steady.csv")
df.to_csv(filename, index=False)

print(f"✅ Prediction saved successfully as {filename}")