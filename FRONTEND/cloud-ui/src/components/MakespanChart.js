import React from "react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid } from "recharts";

const MakespanChart = ({ data }) => {
  return (
    <div>
      <h2>Makespan Comparison</h2>
      <BarChart width={600} height={300} data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="name" />
        <YAxis />
        <Tooltip />
        <Bar dataKey="makespan" />
      </BarChart>
    </div>
  );
};

export default MakespanChart;