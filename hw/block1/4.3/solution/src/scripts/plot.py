import csv
import math
import os
from collections import defaultdict

import matplotlib.pyplot as plt


INPUT_CSV = os.path.join("solution", "results", "raw", "latency_stats.csv")
OUTPUT_PNG = os.path.join("solution", "results", "plot", "release_latency.png")


def read_stats(path):
    data = defaultdict(list)

    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            latch = row["latch"]
            threads = int(row["threads"])
            mean_ns = float(row["mean_nanos"])
            stddev_ns = float(row["stddev_nanos"])

            data[latch].append({
                "threads": threads,
                "mean_ns": mean_ns,
                "stddev_ns": stddev_ns,
            })

    for latch in data:
        data[latch].sort(key=lambda x: x["threads"])

    return data


def ns_to_us(x):
    return x / 1000.0


def ensure_output_dir(path):
    parent = os.path.dirname(path)
    if parent:
        os.makedirs(parent, exist_ok=True)


def plot_stats(data, output_png):
    ensure_output_dir(output_png)

    plt.figure(figsize=(10, 6))

    for latch_name, rows in data.items():
        xs = [row["threads"] for row in rows]
        ys = [ns_to_us(row["mean_ns"]) for row in rows]
        errs = [ns_to_us(row["stddev_ns"]) for row in rows]

        plt.errorbar(
            xs,
            ys,
            yerr=errs,
            marker="o",
            capsize=4,
            label=latch_name
        )

    plt.xlabel("Number of threads (N)")
    plt.ylabel("Release latency (microseconds)")
    plt.title("CountDownLatch release latency vs number of waiting threads")
    plt.legend()
    plt.grid(True)
    plt.xscale("log", base=2)

    plt.tight_layout()
    plt.savefig(output_png, dpi=150)
    plt.close()


def main():
    data = read_stats(INPUT_CSV)
    plot_stats(data, OUTPUT_PNG)
    print(f"Plot saved to: {OUTPUT_PNG}")


if __name__ == "__main__":
    main()