# import pandas as pd
# import matplotlib.pyplot as plt
# from sklearn.metrics import mean_absolute_error, mean_squared_error
# import numpy as np
# import os
#
# # --- Paths ---
# actual_path = "../workloads/sample.csv"
# predicted_path = "../results/predicted_steady.csv"

#
# # --- Load Datasets ---
# actual_df = pd.read_csv(actual_path)
# predicted_df = pd.read_csv(predicted_path)
#
# # --- Convert and Normalize Time ---
# # Convert arrival_time (seconds) → relative timestamp (same as predictor)
# base_time = pd.Timestamp("2025-01-01 00:00:00")
# actual_df["timestamp"] = base_time + pd.to_timedelta(actual_df["arrival_time"], unit="s")
#
# # Group actual workload by minute
# actual_grouped = (
#     actual_df.groupby(actual_df["timestamp"].dt.floor("min"))["length"]
#     .sum()
#     .reset_index(name="actual_length")
# )
#
# # Ensure predicted_df timestamp is datetime
# predicted_df["timestamp"] = pd.to_datetime(predicted_df["timestamp"], errors="coerce")
#
# # --- Merge Actual & Predicted Data ---
#
# merged_df = pd.merge_asof(
#     predicted_df.sort_values("timestamp"),
#     actual_grouped.sort_values("timestamp"),
#     on="timestamp"
# )
#
# print(f"✅ Merged rows: {len(merged_df)}")
# print(merged_df.head())
#
# #--- Plot ---
# # plt.figure(figsize=(10, 5))
# # plt.plot(merged_df["timestamp"], merged_df["actual_length"], label="Actual Workload", linewidth=2)
# # plt.plot(merged_df["timestamp"], merged_df["predicted_length"], label="Predicted Workload", linestyle="dashed", linewidth=2)
# plt.figure(figsize=(10, 5))
# plt.plot(merged_df["timestamp"],
#          merged_df["actual_length"]/merged_df["actual_length"].max(),
#          label="Actual (Normalized)", linewidth=2)
# plt.plot(merged_df["timestamp"],
#          merged_df["predicted_length"]/merged_df["predicted_length"].max(),
#          label="Predicted (Normalized)", linestyle="dashed", linewidth=2)
# plt.xlabel("Time")
# plt.ylabel("Workload Length")
# plt.title("Actual vs Predicted Workload (LSTM)")
# plt.legend()
# plt.grid(True)
# plt.tight_layout()
#
# # Save and show
# os.makedirs(os.path.dirname(output_image), exist_ok=True)
# plt.savefig(output_image)
# plt.show()
#
# # --- Metrics ---
# if len(merged_df) > 0:
#     mae = mean_absolute_error(merged_df["actual_length"], merged_df["predicted_length"])
#     rmse = np.sqrt(mean_squared_error(merged_df["actual_length"], merged_df["predicted_length"]))
#     print(f"📊 MAE: {mae:.2f}")
#     print(f"📊 RMSE: {rmse:.2f}")
# else:
#     print("⚠ No data to plot — check dataset alignment.")
# visualize_results.py
import pandas as pd
import matplotlib.pyplot as plt
from sklearn.metrics import mean_absolute_error, mean_squared_error
import numpy as np

# Load actual vs predicted data
df = pd.read_csv("../results/predicted_steady.csv")

# Convert timestamp
df['Task_Start_Time'] = pd.to_datetime(df['Task_Start_Time'])

mae = mean_absolute_error(df['System_Throughput (tasks/sec)'], df['Predicted_Throughput (tasks/sec)'])
rmse = np.sqrt(mean_squared_error(df['System_Throughput (tasks/sec)'], df['Predicted_Throughput (tasks/sec)']))

print(f"📊 MAE: {mae:.2f}")
print(f"📊 RMSE: {rmse:.2f}")

plt.figure(figsize=(10,6))
plt.plot(df['Task_Start_Time'], df['System_Throughput (tasks/sec)'], label="Actual", color='blue')
plt.plot(df['Task_Start_Time'], df['Predicted_Throughput (tasks/sec)'], label="Predicted", color='orange', linestyle='--')
plt.xlabel("Time")
plt.ylabel("System Throughput (tasks/sec)")
plt.title("Actual vs Predicted System Throughput")
plt.legend()
plt.show()
