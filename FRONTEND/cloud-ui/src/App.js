//import React, { useEffect, useState } from "react";
//import CostChart from "./components/CostChart";
//import MakespanChart from "./components/MakespanChart";
//import VmLoadChart from "./components/VmLoadChart";
//
//function App() {
//  const [data, setData] = useState([]);
//
//  useEffect(() => {
//    fetch("/data/all_schedulers.json")
//      .then((res) => res.json())
//      .then((json) => setData(json.schedulers))
//      .catch((err) => console.error("Error loading JSON:", err));
//  }, []);
//
//  if (!data.length) {
//    return <h2>Loading charts...</h2>;
//  }
//
//  return (
//    <div style={{ padding: "20px", textAlign: "center" }}>
//      <h1>Cloud Scheduler Dashboard</h1>
//
//      <CostChart data={data} />
//      <MakespanChart data={data} />
//      <VmLoadChart data={data} />
//    </div>
//  );
//}
//
//export default App;
import React, { useEffect, useState } from "react";
import CostChart from "./components/CostChart";
import MakespanChart from "./components/MakespanChart";
import VmLoadChart from "./components/VmLoadChart";

function App() {
  const [data, setData] = useState([]);
  const [showCharts, setShowCharts] = useState(false);
  const [selectedScheduler, setSelectedScheduler] = useState(null);

  useEffect(() => {
    fetch("/data/all_schedulers.json")
      .then((res) => res.json())
      .then((json) => setData(json.schedulers));
  }, []);

  const handleSelect = (e) => {
    const selected = data.find((d) => d.name === e.target.value);
    setSelectedScheduler(selected);
  };

  return (
    <div style={{ padding: "20px", textAlign: "center" }}>
      <h1>Cloud Scheduler Dashboard</h1>

      {/* 🔘 Anchor Button */}
      <button
        onClick={() => setShowCharts(true)}
        style={{
          padding: "10px 20px",
          margin: "10px",
          cursor: "pointer",
        }}
      >
        View Graphs
      </button>

      {/* 🔽 Dropdown */}
      <div style={{ margin: "20px" }}>
        <select onChange={handleSelect}>
          <option value="">Select Scheduler</option>
          {data.map((item, index) => (
            <option key={index} value={item.name}>
              {item.name}
            </option>
          ))}
        </select>
      </div>

      {/* 📄 JSON Display */}
      {selectedScheduler && (
        <div
          style={{
            textAlign: "left",
            background: "#f4f4f4",
            padding: "15px",
            margin: "20px",
            borderRadius: "8px",
          }}
        >
          <h3>Scheduler JSON</h3>
          <pre>{JSON.stringify(selectedScheduler, null, 2)}</pre>
        </div>
      )}

      {/* 📊 Charts */}
      {showCharts && (
        <>
          <CostChart data={data} />
          <MakespanChart data={data} />
          <VmLoadChart data={data} />
        </>
      )}
    </div>
  );
}

export default App;