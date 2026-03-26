import React from "react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, Legend, CartesianGrid } from "recharts";

const transformData = (data) => {
  return data.map((item) => ({
    name: item.name,
    VM0: item.vmLoad[0],
    VM1: item.vmLoad[1],
    VM2: item.vmLoad[2],
    VM3: item.vmLoad[3],
    VM4: item.vmLoad[4],
  }));
};

const VmLoadChart = ({ data }) => {
  const formatted = transformData(data);

  return (
    <div>
      <h2>VM Load Distribution</h2>
      <BarChart width={800} height={400} data={formatted}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="name" />
        <YAxis />
        <Tooltip />
        <Legend />
        <Bar dataKey="VM0" />
        <Bar dataKey="VM1" />
        <Bar dataKey="VM2" />
        <Bar dataKey="VM3" />
        <Bar dataKey="VM4" />
      </BarChart>
    </div>
  );
};

export default VmLoadChart;