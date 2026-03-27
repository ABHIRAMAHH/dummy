import React, { useState } from "react";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  RadarChart,
  Radar,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  ResponsiveContainer,
  Cell,
  PieChart,
  Pie,
} from "recharts";

// ─── DATA ────────────────────────────────────────────────────────────────────

const RAW = {
  totalTasks: 1000,
  vmCount: 5,
  fairShare: 200,
  schedulers: [
    {
      id: "ASB_DYNAMIC",
      name: "ASB-Dynamic",
      shortName: "ASB",
      makespan: 235.58,
      avgWait: 0.1,
      totalCost: 49355,
      improvPercent: 86.56,
      loadStdDev: 36.94,
      loadBalanced: true,
      vmBindings: [170, 172, 191, 196, 271],
      backlogSec: [235.58, 237.26, 130.08, 131.42, 80.08],
      weights: {
        CPEFT: 0.39,
        Priority: 0.18,
        AI: 0.17,
        Affinity: 0.09,
        Overload: 0.17,
      },
      highlight: true,
      isProposed: true,
    },
    {
      id: "PRIORITY",
      name: "Priority",
      shortName: "PRI",
      makespan: 5998.26,
      avgWait: 0.1,
      totalCost: 67127,
      improvPercent: 81.71,
      loadStdDev: 62.69,
      loadBalanced: false,
      vmBindings: [132, 115, 251, 251, 251],
      backlogSec: [15644, 4538, 7732, 8325, 3298],
    },
    {
      id: "FIFO",
      name: "FIFO (Baseline)",
      shortName: "FIFO",
      makespan: 125.63,
      avgWait: 0.1,
      totalCost: 367130,
      improvPercent: 0,
      loadStdDev: 95.37,
      loadBalanced: false,
      vmBindings: [124, 77, 237, 210, 352],
      backlogSec: [3240, 2552, 5758, 5654, 6045],
      isBaseline: true,
    },
    {
      id: "ROUND_ROBIN",
      name: "Round Robin",
      shortName: "RR",
      makespan: 25689.85,
      avgWait: 0.1,
      totalCost: 1121001,
      improvPercent: -205.34,
      loadStdDev: 0,
      loadBalanced: true,
      vmBindings: [200, 200, 200, 200, 200],
      backlogSec: [23752, 25689, 6020, 6634, 1994],
    },
    {
      id: "PRIORITY_SJF",
      name: "Priority SJF",
      shortName: "PSJF",
      makespan: 24889.98,
      avgWait: 0.1,
      totalCost: 1119107,
      improvPercent: -204.83,
      loadStdDev: 0,
      loadBalanced: true,
      vmBindings: [200, 200, 200, 200, 200],
      backlogSec: [24889, 24678, 6293, 6306, 2002],
    },
    {
      id: "SJF",
      name: "SJF",
      shortName: "SJF",
      makespan: 5554.64,
      avgWait: 0.1,
      totalCost: 1241126,
      improvPercent: -238.1,
      loadStdDev: 141.59,
      loadBalanced: false,
      vmBindings: [66, 107, 165, 193, 469],
      backlogSec: [5930, 6396, 5585, 5668, 5554],
    },
    {
      id: "MINMIN",
      name: "MinMin",
      shortName: "MM",
      makespan: 5056.95,
      avgWait: 0.1,
      totalCost: 1244132,
      improvPercent: -238.92,
      loadStdDev: 267.4,
      loadBalanced: false,
      vmBindings: [21, 20, 118, 113, 728],
      backlogSec: [6619, 6233, 6247, 6366, 5056],
    },
    {
      id: "COST_GREEDY",
      name: "Cost Greedy",
      shortName: "CG",
      makespan: 123996.37,
      avgWait: 0.1,
      totalCost: 123996,
      improvPercent: 66.23,
      loadStdDev: 400,
      loadBalanced: false,
      vmBindings: [1000, 0, 0, 0, 0],
      backlogSec: [123996, 0, 0, 0, 0],
    },
  ],
  vms: [
    { id: 0, type: "Small", mips: 1000, cost: 1.0, color: "#6366f1" },
    { id: 1, type: "Small", mips: 1000, cost: 1.0, color: "#8b5cf6" },
    { id: 2, type: "Medium", mips: 2000, cost: 50.0, color: "#06b6d4" },
    { id: 3, type: "Medium", mips: 2000, cost: 50.0, color: "#0ea5e9" },
    { id: 4, type: "Large", mips: 3000, cost: 120.0, color: "#f59e0b" },
  ],
};

// ─── COLOUR PALETTE ──────────────────────────────────────────────────────────

const COLORS = {
  proposed: "#6366f1",
  baseline: "#94a3b8",
  good: "#22c55e",
  warn: "#f59e0b",
  bad: "#ef4444",
  neutral: "#64748b",
  bg: "#0f172a",
  card: "#1e293b",
  cardHover: "#273449",
  border: "#334155",
  text: "#f1f5f9",
  muted: "#94a3b8",
  accent: "#818cf8",
};

const SCHEDULER_COLORS = [
  "#6366f1",
  "#22c55e",
  "#94a3b8",
  "#f59e0b",
  "#ec4899",
  "#06b6d4",
  "#ef4444",
  "#a855f7",
];

// ─── HELPERS ─────────────────────────────────────────────────────────────────

const fmt = (n, dec = 2) =>
  n >= 1_000_000
    ? `${(n / 1_000_000).toFixed(1)}M`
    : n >= 1_000
    ? `${(n / 1_000).toFixed(1)}K`
    : Number(n).toFixed(dec);

const pctColor = (v) =>
  v > 50 ? COLORS.good : v > 0 ? COLORS.warn : COLORS.bad;

const badgeStyle = (v) => ({
  display: "inline-flex",
  alignItems: "center",
  gap: 4,
  padding: "2px 10px",
  borderRadius: 999,
  fontSize: 12,
  fontWeight: 600,
  background: v > 0 ? "rgba(34,197,94,0.15)" : "rgba(239,68,68,0.15)",
  color: v > 0 ? COLORS.good : COLORS.bad,
  border: `1px solid ${v > 0 ? "rgba(34,197,94,0.3)" : "rgba(239,68,68,0.3)"}`,
});

// ─── SHARED STYLES ───────────────────────────────────────────────────────────

const S = {
  page: {
    minHeight: "100vh",
    background: COLORS.bg,
    color: COLORS.text,
    fontFamily: "'Inter', system-ui, sans-serif",
    padding: "24px",
  },
  card: {
    background: COLORS.card,
    border: `1px solid ${COLORS.border}`,
    borderRadius: 16,
    padding: 24,
  },
  cardSm: {
    background: COLORS.card,
    border: `1px solid ${COLORS.border}`,
    borderRadius: 12,
    padding: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 700,
    color: COLORS.text,
    marginBottom: 20,
    display: "flex",
    alignItems: "center",
    gap: 8,
  },
  label: {
    fontSize: 12,
    color: COLORS.muted,
    marginBottom: 4,
    textTransform: "uppercase",
    letterSpacing: "0.05em",
  },
  value: { fontSize: 28, fontWeight: 800, color: COLORS.text },
  grid2: { display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 16 },
  grid3: { display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16 },
  grid4: { display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16 },
  flex: { display: "flex", alignItems: "center", gap: 12 },
  tag: {
    fontSize: 11,
    fontWeight: 600,
    padding: "2px 8px",
    borderRadius: 6,
    background: "rgba(99,102,241,0.15)",
    color: COLORS.accent,
    border: "1px solid rgba(99,102,241,0.3)",
  },
};

// ─── CUSTOM TOOLTIP ──────────────────────────────────────────────────────────

const CustomTooltip = ({ active, payload, label, formatter }) => {
  if (!active || !payload?.length) return null;
  return (
    <div
      style={{
        background: "#1e293b",
        border: `1px solid ${COLORS.border}`,
        borderRadius: 10,
        padding: "10px 14px",
        fontSize: 13,
      }}
    >
      {label && (
        <div style={{ color: COLORS.muted, marginBottom: 6, fontWeight: 600 }}>
          {label}
        </div>
      )}
      {payload.map((p, i) => (
        <div key={i} style={{ color: p.color || COLORS.text, marginBottom: 2 }}>
          <span style={{ color: COLORS.muted }}>{p.name}: </span>
          <strong>
            {formatter ? formatter(p.value, p.name) : fmt(p.value)}
          </strong>
        </div>
      ))}
    </div>
  );
};

// ─── SECTION: HEADER ─────────────────────────────────────────────────────────

const Header = () => (
  <div style={{ marginBottom: 32 }}>
    <div
      style={{
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: 16,
      }}
    >
      <div>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 12,
            marginBottom: 8,
          }}
        >
          <div
            style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              background: "linear-gradient(135deg,#6366f1,#8b5cf6)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: 22,
            }}
          >
            ⚡
          </div>
          <div>
            <h1
              style={{
                margin: 0,
                fontSize: 26,
                fontWeight: 800,
                background: "linear-gradient(90deg,#818cf8,#c084fc)",
                WebkitBackgroundClip: "text",
                WebkitTextFillColor: "transparent",
              }}
            >
              Cloud Scheduler Comparison
            </h1>
            <p style={{ margin: 0, color: COLORS.muted, fontSize: 14 }}>
              ASB-Dynamic v4 vs 7 baseline schedulers · 1,000 tasks · 5 VMs
            </p>
          </div>
        </div>
      </div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
        {[
          { label: "Total Tasks", value: "1,000" },
          { label: "VM Count", value: "5" },
          { label: "Fair Share", value: "200 / VM" },
        ].map(({ label, value }) => (
          <div
            key={label}
            style={{ ...S.cardSm, textAlign: "center", minWidth: 100 }}
          >
            <div style={S.label}>{label}</div>
            <div
              style={{ fontSize: 20, fontWeight: 700, color: COLORS.accent }}
            >
              {value}
            </div>
          </div>
        ))}
      </div>
    </div>
  </div>
);

// ─── SECTION: KPI CARDS ──────────────────────────────────────────────────────

const KpiCards = () => {
  const asb = RAW.schedulers[0];
  const fifo = RAW.schedulers[2];
  const cards = [
    {
      icon: "🏆",
      label: "Best Makespan",
      value: `${fmt(asb.makespan)}s`,
      sub: `vs FIFO ${fmt(fifo.makespan)}s`,
      accent: COLORS.good,
      note: "ASB-Dynamic wins",
    },
    {
      icon: "📉",
      label: "Cost Reduction",
      value: `${fmt(asb.improvPercent)}%`,
      sub: `$${fmt(asb.totalCost)} vs $${fmt(fifo.totalCost)}`,
      accent: COLORS.proposed,
      note: "vs FIFO baseline",
    },
    {
      icon: "⚖️",
      label: "Load Std Dev",
      value: fmt(asb.loadStdDev, 1),
      sub: "tasks σ across VMs",
      accent: COLORS.warn,
      note: "Balanced ✓",
    },
    {
      icon: "🤖",
      label: "AI Predictions",
      value: "1,000",
      sub: "throughput windows",
      accent: "#06b6d4",
      note: "LSTM offline model",
    },
  ];
  return (
    <div style={{ ...S.grid4, marginBottom: 24 }}>
      {cards.map(({ icon, label, value, sub, accent, note }) => (
        <div
          key={label}
          style={{
            ...S.card,
            borderTop: `3px solid ${accent}`,
            position: "relative",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              position: "absolute",
              top: 16,
              right: 16,
              fontSize: 28,
              opacity: 0.15,
            }}
          >
            {icon}
          </div>
          <div style={{ fontSize: 24, marginBottom: 4 }}>{icon}</div>
          <div style={S.label}>{label}</div>
          <div style={{ ...S.value, color: accent }}>{value}</div>
          <div style={{ fontSize: 12, color: COLORS.muted, marginTop: 4 }}>
            {sub}
          </div>
          <div style={{ ...S.tag, marginTop: 8, display: "inline-block" }}>
            {note}
          </div>
        </div>
      ))}
    </div>
  );
};

// ─── SECTION: MAKESPAN COMPARISON BAR ────────────────────────────────────────

const MakespanChart = () => {
  const data = RAW.schedulers.map((s, i) => ({
    name: s.shortName,
    makespan: s.makespan,
    full: s.name,
    color: s.isProposed
      ? COLORS.proposed
      : s.isBaseline
      ? COLORS.baseline
      : SCHEDULER_COLORS[i],
  }));

  return (
    <div style={S.card}>
      <div style={S.sectionTitle}>
        <span>📊</span> Makespan Comparison (seconds)
        <span style={{ ...S.tag, marginLeft: "auto", fontSize: 11 }}>
          Lower is better
        </span>
      </div>
      <ResponsiveContainer width="100%" height={280}>
        <BarChart data={data} margin={{ top: 4, right: 8, left: 8, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={COLORS.border} />
          <XAxis
            dataKey="name"
            tick={{ fill: COLORS.muted, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            tick={{ fill: COLORS.muted, fontSize: 11 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) => fmt(v, 0)}
          />
          <Tooltip
            content={<CustomTooltip formatter={(v) => `${fmt(v)}s`} />}
          />
          <Bar dataKey="makespan" radius={[6, 6, 0, 0]} name="Makespan (s)">
            {data.map((d, i) => (
              <Cell
                key={i}
                fill={d.color}
                opacity={d.name === "ASB" ? 1 : 0.65}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      <div
        style={{ display: "flex", flexWrap: "wrap", gap: 10, marginTop: 12 }}
      >
        {data.map((d) => (
          <div
            key={d.name}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 5,
              fontSize: 12,
              color: COLORS.muted,
            }}
          >
            <div
              style={{
                width: 10,
                height: 10,
                borderRadius: 3,
                background: d.color,
              }}
            />
            {d.full}
          </div>
        ))}
      </div>
    </div>
  );
};

// ─── SECTION: COST COMPARISON ────────────────────────────────────────────────

const CostChart = () => {
  const data = RAW.schedulers.map((s, i) => ({
    name: s.shortName,
    cost: s.totalCost,
    full: s.name,
    color: s.isProposed
      ? COLORS.proposed
      : s.isBaseline
      ? COLORS.baseline
      : SCHEDULER_COLORS[i],
  }));

  return (
    <div style={S.card}>
      <div style={S.sectionTitle}>
        <span>💰</span> Total Cost Comparison ($)
        <span style={{ ...S.tag, marginLeft: "auto", fontSize: 11 }}>
          Lower is better
        </span>
      </div>
      <ResponsiveContainer width="100%" height={280}>
        <BarChart data={data} margin={{ top: 4, right: 8, left: 8, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={COLORS.border} />
          <XAxis
            dataKey="name"
            tick={{ fill: COLORS.muted, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            tick={{ fill: COLORS.muted, fontSize: 11 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) => `$${fmt(v, 0)}`}
          />
          <Tooltip
            content={<CustomTooltip formatter={(v) => `$${fmt(v)}`} />}
          />
          <Bar dataKey="cost" radius={[6, 6, 0, 0]} name="Total Cost ($)">
            {data.map((d, i) => (
              <Cell
                key={i}
                fill={d.color}
                opacity={d.name === "ASB" ? 1 : 0.65}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
};

// ─── SECTION: IMPROVEMENT % ──────────────────────────────────────────────────

const ImprovementChart = () => {
  const data = RAW.schedulers
    .filter((s) => !s.isBaseline)
    .map((s, i) => ({
      name: s.shortName,
      full: s.name,
      impr: s.improvPercent,
      color: s.improvPercent > 0 ? COLORS.good : COLORS.bad,
    }))
    .sort((a, b) => b.impr - a.impr);

  return (
    <div style={S.card}>
      <div style={S.sectionTitle}>
        <span>📈</span> Improvement vs FIFO Baseline (%)
        <span style={{ ...S.tag, marginLeft: "auto", fontSize: 11 }}>
          Higher is better
        </span>
      </div>
      <ResponsiveContainer width="100%" height={260}>
        <BarChart
          data={data}
          layout="vertical"
          margin={{ top: 4, right: 40, left: 20, bottom: 4 }}
        >
          <CartesianGrid
            strokeDasharray="3 3"
            stroke={COLORS.border}
            horizontal={false}
          />
          <XAxis
            type="number"
            tick={{ fill: COLORS.muted, fontSize: 11 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) => `${v}%`}
            domain={["dataMin - 20", "dataMax + 10"]}
          />
          <YAxis
            type="category"
            dataKey="name"
            tick={{ fill: COLORS.muted, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            width={40}
          />
          <Tooltip
            content={<CustomTooltip formatter={(v) => `${fmt(v, 2)}%`} />}
          />
          <Bar dataKey="impr" radius={[0, 6, 6, 0]} name="Improvement (%)">
            {data.map((d, i) => (
              <Cell key={i} fill={d.color} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
};

// ─── SECTION: VM BINDING DISTRIBUTION ───────────────────────────────────────

const VMBindingChart = ({ schedulerId }) => {
  const s =
    RAW.schedulers.find((x) => x.id === schedulerId) || RAW.schedulers[0];
  const data = RAW.vms.map((vm, i) => ({
    name: `VM${vm.id} (${vm.type})`,
    tasks: s.vmBindings[i],
    backlog: Math.round(s.backlogSec[i]),
    color: vm.color,
    mips: vm.mips,
    cost: vm.cost,
  }));

  return (
    <div style={S.card}>
      <div style={S.sectionTitle}>
        <span>🖥️</span> VM Task Distribution — {s.name}
      </div>
      <div
        style={{ display: "flex", gap: 16, marginBottom: 16, flexWrap: "wrap" }}
      >
        <div style={S.cardSm}>
          <div style={S.label}>Std Dev</div>
          <div
            style={{
              fontSize: 20,
              fontWeight: 700,
              color: s.loadStdDev < 60 ? COLORS.good : COLORS.bad,
            }}
          >
            σ = {fmt(s.loadStdDev, 1)}
          </div>
        </div>
        <div style={S.cardSm}>
          <div style={S.label}>Balanced</div>
          <div
            style={{
              fontSize: 20,
              fontWeight: 700,
              color: s.loadBalanced ? COLORS.good : COLORS.bad,
            }}
          >
            {s.loadBalanced ? "✓ Yes" : "✗ No"}
          </div>
        </div>
        <div style={S.cardSm}>
          <div style={S.label}>Fair Share</div>
          <div style={{ fontSize: 20, fontWeight: 700, color: COLORS.accent }}>
            200 / VM
          </div>
        </div>
      </div>
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={data} margin={{ top: 4, right: 8, left: 8, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={COLORS.border} />
          <XAxis
            dataKey="name"
            tick={{ fill: COLORS.muted, fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            tick={{ fill: COLORS.muted, fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip
            content={
              <CustomTooltip
                formatter={(v, n) => (n === "backlog" ? `${fmt(v)}s` : v)}
              />
            }
          />
          <Legend wrapperStyle={{ color: COLORS.muted, fontSize: 12 }} />
          <Bar dataKey="tasks" name="Tasks Assigned" radius={[4, 4, 0, 0]}>
            {data.map((d, i) => (
              <Cell key={i} fill={d.color} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      {/* Fair share reference line annotation */}
      <div
        style={{
          textAlign: "center",
          fontSize: 12,
          color: COLORS.muted,
          marginTop: 8,
        }}
      >
        Fair share reference:{" "}
        <strong style={{ color: COLORS.accent }}>200 tasks per VM</strong>
      </div>
    </div>
  );
};

// ─── SECTION: ASB WEIGHT RADAR ───────────────────────────────────────────────

const WeightRadar = () => {
  const asb = RAW.schedulers[0];
  const data = Object.entries(asb.weights).map(([k, v]) => ({
    subject: k,
    value: Math.round(v * 100),
    fullMark: 50,
  }));

  return (
    <div style={S.card}>
      <div style={S.sectionTitle}>
        <span>🎯</span> ASB-Dynamic Weight Distribution
      </div>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 24,
          flexWrap: "wrap",
        }}
      >
        <ResponsiveContainer width={260} height={240}>
          <RadarChart data={data}>
            <PolarGrid stroke={COLORS.border} />
            <PolarAngleAxis
              dataKey="subject"
              tick={{ fill: COLORS.muted, fontSize: 12 }}
            />
            <PolarRadiusAxis
              angle={90}
              domain={[0, 50]}
              tick={{ fill: COLORS.muted, fontSize: 10 }}
            />
            <Radar
              name="Weight %"
              dataKey="value"
              stroke={COLORS.proposed}
              fill={COLORS.proposed}
              fillOpacity={0.25}
              dot={{ fill: COLORS.proposed, r: 4 }}
            />
            <Tooltip content={<CustomTooltip formatter={(v) => `${v}%`} />} />
          </RadarChart>
        </ResponsiveContainer>
        <div style={{ flex: 1, minWidth: 180 }}>
          {data.map(({ subject, value }) => (
            <div key={subject} style={{ marginBottom: 12 }}>
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  marginBottom: 4,
                }}
              >
                <span style={{ fontSize: 13, color: COLORS.text }}>
                  {subject}
                </span>
                <span
                  style={{
                    fontSize: 13,
                    fontWeight: 700,
                    color: COLORS.accent,
                  }}
                >
                  {value}%
                </span>
              </div>
              <div
                style={{
                  height: 6,
                  background: COLORS.border,
                  borderRadius: 99,
                  overflow: "hidden",
                }}
              >
                <div
                  style={{
                    height: "100%",
                    borderRadius: 99,
                    width: `${(value / 50) * 100}%`,
                    background: `linear-gradient(90deg, ${COLORS.proposed}, #c084fc)`,
                    transition: "width 0.6s ease",
                  }}
                />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

// ─── SECTION: VM CONFIG TABLE ─────────────────────────────────────────────────

const VMConfigTable = () => (
  <div style={S.card}>
    <div style={S.sectionTitle}>
      <span>🔧</span> VM Configuration
    </div>
    <div style={{ overflowX: "auto" }}>
      <table
        style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}
      >
        <thead>
          <tr>
            {["VM ID", "Type", "MIPS", "Cost/sec", "Speed Tier"].map((h) => (
              <th
                key={h}
                style={{
                  textAlign: "left",
                  padding: "8px 12px",
                  color: COLORS.muted,
                  fontWeight: 600,
                  borderBottom: `1px solid ${COLORS.border}`,
                  fontSize: 11,
                  textTransform: "uppercase",
                  letterSpacing: "0.05em",
                }}
              >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {RAW.vms.map((vm) => (
            <tr
              key={vm.id}
              style={{ borderBottom: `1px solid ${COLORS.border}` }}
            >
              <td style={{ padding: "10px 12px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <div
                    style={{
                      width: 10,
                      height: 10,
                      borderRadius: "50%",
                      background: vm.color,
                    }}
                  />
                  VM {vm.id}
                </div>
              </td>
              <td style={{ padding: "10px 12px" }}>
                <span
                  style={{
                    padding: "2px 10px",
                    borderRadius: 6,
                    fontSize: 11,
                    fontWeight: 600,
                    background:
                      vm.type === "Large"
                        ? "rgba(245,158,11,0.15)"
                        : vm.type === "Medium"
                        ? "rgba(6,182,212,0.15)"
                        : "rgba(99,102,241,0.15)",
                    color:
                      vm.type === "Large"
                        ? COLORS.warn
                        : vm.type === "Medium"
                        ? "#06b6d4"
                        : COLORS.accent,
                  }}
                >
                  {vm.type}
                </span>
              </td>
              <td
                style={{
                  padding: "10px 12px",
                  color: COLORS.text,
                  fontWeight: 600,
                }}
              >
                {vm.mips.toLocaleString()}
              </td>
              <td style={{ padding: "10px 12px", color: COLORS.text }}>
                ${vm.cost.toFixed(2)}
              </td>
              <td style={{ padding: "10px 12px" }}>
                <div style={{ display: "flex", gap: 3 }}>
                  {Array.from({ length: vm.mips / 1000 }).map((_, i) => (
                    <div
                      key={i}
                      style={{
                        width: 8,
                        height: 16,
                        borderRadius: 2,
                        background: vm.color,
                        opacity: 0.8,
                      }}
                    />
                  ))}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  </div>
);

// ─── SECTION: FULL COMPARISON TABLE ──────────────────────────────────────────

const ComparisonTable = ({ activeId, setActiveId }) => (
  <div style={S.card}>
    <div style={S.sectionTitle}>
      <span>📋</span> Full Scheduler Comparison
    </div>
    <div style={{ overflowX: "auto" }}>
      <table
        style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}
      >
        <thead>
          <tr>
            {[
              "Scheduler",
              "Makespan (s)",
              "Avg Wait (s)",
              "Total Cost ($)",
              "vs FIFO",
              "Std Dev",
              "Balanced",
              "",
            ].map((h) => (
              <th
                key={h}
                style={{
                  textAlign: "left",
                  padding: "8px 12px",
                  color: COLORS.muted,
                  fontWeight: 600,
                  borderBottom: `1px solid ${COLORS.border}`,
                  fontSize: 11,
                  textTransform: "uppercase",
                  letterSpacing: "0.05em",
                  whiteSpace: "nowrap",
                }}
              >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {RAW.schedulers.map((s, i) => {
            const isActive = activeId === s.id;
            return (
              <tr
                key={s.id}
                onClick={() => setActiveId(s.id)}
                style={{
                  borderBottom: `1px solid ${COLORS.border}`,
                  background: isActive
                    ? "rgba(99,102,241,0.1)"
                    : s.isProposed
                    ? "rgba(99,102,241,0.04)"
                    : "transparent",
                  cursor: "pointer",
                  transition: "background 0.15s",
                }}
              >
                <td style={{ padding: "11px 12px" }}>
                  <div
                    style={{ display: "flex", alignItems: "center", gap: 8 }}
                  >
                    <div
                      style={{
                        width: 10,
                        height: 10,
                        borderRadius: "50%",
                        background: SCHEDULER_COLORS[i],
                        flexShrink: 0,
                      }}
                    />
                    <span
                      style={{
                        fontWeight: s.isProposed ? 700 : 400,
                        color: s.isProposed ? COLORS.accent : COLORS.text,
                      }}
                    >
                      {s.name}
                    </span>
                    {s.isProposed && (
                      <span style={{ ...S.tag, fontSize: 10 }}>★ Proposed</span>
                    )}
                    {s.isBaseline && (
                      <span
                        style={{
                          fontSize: 10,
                          padding: "1px 6px",
                          borderRadius: 4,
                          background: "rgba(148,163,184,0.15)",
                          color: COLORS.muted,
                        }}
                      >
                        Baseline
                      </span>
                    )}
                  </div>
                </td>
                <td
                  style={{
                    padding: "11px 12px",
                    fontWeight: 600,
                    color: COLORS.text,
                  }}
                >
                  {fmt(s.makespan)}
                </td>
                <td style={{ padding: "11px 12px", color: COLORS.text }}>
                  {fmt(s.avgWait, 2)}
                </td>
                <td style={{ padding: "11px 12px", color: COLORS.text }}>
                  ${fmt(s.totalCost)}
                </td>
                <td style={{ padding: "11px 12px" }}>
                  {!s.isBaseline && (
                    <span style={badgeStyle(s.improvPercent)}>
                      {s.improvPercent > 0 ? "▲" : "▼"}{" "}
                      {Math.abs(s.improvPercent).toFixed(1)}%
                    </span>
                  )}
                  {s.isBaseline && (
                    <span style={{ color: COLORS.muted }}>—</span>
                  )}
                </td>
                <td
                  style={{
                    padding: "11px 12px",
                    color:
                      s.loadStdDev < 60
                        ? COLORS.good
                        : s.loadStdDev < 150
                        ? COLORS.warn
                        : COLORS.bad,
                    fontWeight: 600,
                  }}
                >
                  {fmt(s.loadStdDev, 1)}
                </td>
                <td style={{ padding: "11px 12px" }}>
                  <span
                    style={{
                      color: s.loadBalanced ? COLORS.good : COLORS.bad,
                      fontWeight: 600,
                    }}
                  >
                    {s.loadBalanced ? "✓" : "✗"}
                  </span>
                </td>
                <td style={{ padding: "11px 12px" }}>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setActiveId(s.id);
                    }}
                    style={{
                      padding: "3px 10px",
                      borderRadius: 6,
                      fontSize: 11,
                      fontWeight: 600,
                      cursor: "pointer",
                      background: isActive ? COLORS.proposed : "transparent",
                      color: isActive ? "#fff" : COLORS.muted,
                      border: `1px solid ${
                        isActive ? COLORS.proposed : COLORS.border
                      }`,
                      transition: "all 0.15s",
                    }}
                  >
                    {isActive ? "Selected" : "Inspect"}
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
    <div style={{ marginTop: 12, fontSize: 12, color: COLORS.muted }}>
      💡 Click any row to inspect VM binding distribution in the chart above.
    </div>
  </div>
);

// ─── SECTION: BACKLOG HEATMAP ─────────────────────────────────────────────────

const BacklogHeatmap = () => {
  const maxBacklog = Math.max(...RAW.schedulers.flatMap((s) => s.backlogSec));

  return (
    <div style={S.card}>
      <div style={S.sectionTitle}>
        <span>🌡️</span> VM Backlog Heatmap (seconds)
      </div>
      <div style={{ overflowX: "auto" }}>
        <table
          style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}
        >
          <thead>
            <tr>
              <th
                style={{
                  textAlign: "left",
                  padding: "6px 10px",
                  color: COLORS.muted,
                  fontWeight: 600,
                  fontSize: 11,
                  textTransform: "uppercase",
                }}
              >
                Scheduler
              </th>
              {RAW.vms.map((vm) => (
                <th
                  key={vm.id}
                  style={{
                    textAlign: "center",
                    padding: "6px 10px",
                    color: COLORS.muted,
                    fontWeight: 600,
                    fontSize: 11,
                    textTransform: "uppercase",
                  }}
                >
                  VM{vm.id}
                  <br />
                  <span style={{ fontWeight: 400, fontSize: 10 }}>
                    {vm.mips} MIPS
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {RAW.schedulers.map((s, si) => (
              <tr
                key={s.id}
                style={{ borderTop: `1px solid ${COLORS.border}` }}
              >
                <td
                  style={{
                    padding: "8px 10px",
                    fontWeight: s.isProposed ? 700 : 400,
                    color: s.isProposed ? COLORS.accent : COLORS.text,
                    whiteSpace: "nowrap",
                  }}
                >
                  {s.name}
                </td>
                {s.backlogSec.map((val, vi) => {
                  const intensity = val / maxBacklog;
                  const r = Math.round(intensity * 239 + (1 - intensity) * 30);
                  const g = Math.round(intensity * 68 + (1 - intensity) * 41);
                  const b = Math.round(intensity * 68 + (1 - intensity) * 59);
                  return (
                    <td
                      key={vi}
                      style={{ padding: "8px 10px", textAlign: "center" }}
                    >
                      <div
                        style={{
                          background: `rgba(${r},${g},${b},${
                            0.15 + intensity * 0.7
                          })`,
                          borderRadius: 6,
                          padding: "4px 6px",
                          color: intensity > 0.5 ? "#fca5a5" : COLORS.text,
                          fontWeight: 600,
                          fontSize: 11,
                        }}
                      >
                        {fmt(val, 0)}s
                      </div>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          marginTop: 12,
          fontSize: 11,
          color: COLORS.muted,
        }}
      >
        <div style={{ display: "flex", gap: 2 }}>
          {[0.1, 0.3, 0.5, 0.7, 0.9].map((v) => (
            <div
              key={v}
              style={{
                width: 20,
                height: 10,
                borderRadius: 2,
                background: `rgba(${Math.round(v * 239 + 30)},${Math.round(
                  v * 68 + 41
                )},${Math.round(v * 68 + 59)},${0.15 + v * 0.7})`,
              }}
            />
          ))}
        </div>
        Low backlog → High backlog
      </div>
    </div>
  );
};

// ─── SECTION: DONUT — VM BINDINGS FOR ASB ────────────────────────────────────

const BindingDonut = ({ schedulerId }) => {
  const s =
    RAW.schedulers.find((x) => x.id === schedulerId) || RAW.schedulers[0];
  const data = RAW.vms
    .map((vm, i) => ({
      name: `VM${vm.id} (${vm.type})`,
      value: s.vmBindings[i],
      color: vm.color,
    }))
    .filter((d) => d.value > 0);

  return (
    <div style={S.card}>
      <div style={S.sectionTitle}>
        <span>🍩</span> Task Share — {s.name}
      </div>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 24,
          flexWrap: "wrap",
        }}
      >
        <ResponsiveContainer width={200} height={200}>
          <PieChart>
            <Pie
              data={data}
              cx="50%"
              cy="50%"
              innerRadius={55}
              outerRadius={85}
              dataKey="value"
              paddingAngle={3}
            >
              {data.map((d, i) => (
                <Cell key={i} fill={d.color} />
              ))}
            </Pie>
            <Tooltip
              content={<CustomTooltip formatter={(v) => `${v} tasks`} />}
            />
          </PieChart>
        </ResponsiveContainer>
        <div style={{ flex: 1 }}>
          {data.map((d) => (
            <div
              key={d.name}
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                marginBottom: 8,
                fontSize: 13,
              }}
            >
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                <div
                  style={{
                    width: 10,
                    height: 10,
                    borderRadius: "50%",
                    background: d.color,
                  }}
                />
                <span style={{ color: COLORS.muted }}>{d.name}</span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <span style={{ fontWeight: 700, color: COLORS.text }}>
                  {d.value}
                </span>
                <span style={{ color: COLORS.muted, fontSize: 11 }}>
                  ({((d.value / 1000) * 100).toFixed(1)}%)
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

// ─── SECTION: NOTES / LEGEND ─────────────────────────────────────────────────

const Notes = () => (
  <div style={{ ...S.card, borderLeft: `4px solid ${COLORS.proposed}` }}>
    <div style={S.sectionTitle}>
      <span>ℹ️</span> Methodology Notes
    </div>
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))",
        gap: 12,
      }}
    >
      {[
        {
          icon: "📌",
          title: "Baseline",
          body: "FIFO is the reference scheduler. All improvement % values are relative to FIFO makespan.",
        },
        {
          icon: "🔮",
          title: "Predicted Metrics",
          body: "Schedulers without direct CloudSim output use max backlogSec as makespan and sum(backlog × cost) as total cost.",
        },
        {
          icon: "⚡",
          title: "Overload Flag",
          body: "A VM is marked overloaded when its task count significantly exceeds fair share (200 tasks).",
        },
        {
          icon: "🤖",
          title: "ASB-Dynamic Model",
          body: "Uses CPEFT + Priority + AI throughput + Affinity + Overload weights. LSTM predictions cover 1,000 time windows.",
        },
      ].map(({ icon, title, body }) => (
        <div
          key={title}
          style={{
            background: "rgba(99,102,241,0.05)",
            borderRadius: 10,
            padding: 14,
            border: `1px solid ${COLORS.border}`,
          }}
        >
          <div
            style={{ fontWeight: 700, marginBottom: 4, color: COLORS.accent }}
          >
            {icon} {title}
          </div>
          <div style={{ fontSize: 12, color: COLORS.muted, lineHeight: 1.6 }}>
            {body}
          </div>
        </div>
      ))}
    </div>
  </div>
);

// ─── ROOT APP ─────────────────────────────────────────────────────────────────

export default function App() {
  const [activeSchedulerId, setActiveSchedulerId] = useState("ASB_DYNAMIC");

  return (
    <div style={S.page}>
      <div style={{ maxWidth: 1400, margin: "0 auto" }}>
        <Header />
        <KpiCards />

        {/* Row: Makespan + Cost */}
        <div style={{ ...S.grid2, marginBottom: 24 }}>
          <MakespanChart />
          <CostChart />
        </div>

        {/* Row: Improvement + Radar */}
        <div style={{ ...S.grid2, marginBottom: 24 }}>
          <ImprovementChart />
          <WeightRadar />
        </div>

        {/* Row: VM Binding + Donut */}
        <div style={{ ...S.grid2, marginBottom: 24 }}>
          <VMBindingChart schedulerId={activeSchedulerId} />
          <BindingDonut schedulerId={activeSchedulerId} />
        </div>

        {/* Full table */}
        <div style={{ marginBottom: 24 }}>
          <ComparisonTable
            activeId={activeSchedulerId}
            setActiveId={setActiveSchedulerId}
          />
        </div>

        {/* Backlog heatmap */}
        <div style={{ marginBottom: 24 }}>
          <BacklogHeatmap />
        </div>

        {/* VM config + notes */}
        <div style={{ ...S.grid2, marginBottom: 24 }}>
          <VMConfigTable />
          <Notes />
        </div>

        {/* Footer */}
        <div
          style={{
            textAlign: "center",
            padding: "20px 0",
            color: COLORS.muted,
            fontSize: 12,
            borderTop: `1px solid ${COLORS.border}`,
          }}
        >
          ASB-Dynamic Scheduler · Cloud Simulation Results · 1,000 tasks · 5 VMs
          · 8 Schedulers
        </div>
      </div>
    </div>
  );
}
