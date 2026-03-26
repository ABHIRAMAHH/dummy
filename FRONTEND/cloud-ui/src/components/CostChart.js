import React from "react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid } from "recharts";

const CostChart = ({ data }) => {
  return (
    <div>
      <h2>Cost Comparison</h2>
      <BarChart width={600} height={300} data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="name" />
        <YAxis />
        <Tooltip />
        <Bar dataKey="cost" />
      </BarChart>
    </div>
  );
};

export default CostChart;