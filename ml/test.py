import pandas as pd
import numpy as np

def generate_realistic_workload(num_points=5000):
    np.random.seed(42)
    t = np.arange(num_points)

    # Smooth daily pattern (sinusoidal curve)
    base_load = 0.5 + 0.4 * np.sin(2 * np.pi * t / 1440)

    # Random spikes to simulate peak workload
    spikes = np.random.choice([0, 1], size=num_points, p=[0.95, 0.05])
    spike_values = spikes * np.random.uniform(0.5, 1.0, num_points)

    # Random noise
    noise = np.random.normal(0, 0.05, num_points)

    workload = base_load + spike_values + noise
    workload = np.clip(workload, 0, 1)  # keep in [0,1] range

    timestamps = pd.date_range("2025-01-01", periods=num_points, freq="min")
    df = pd.DataFrame({"timestamp": timestamps, "workload_length": workload})
    df.to_csv("workload_data.csv", index=False)
    print("✅ Synthetic dataset generated: workload_data.csv")

generate_realistic_workload()