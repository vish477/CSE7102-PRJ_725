const express = require("express");
const cors = require("cors");
const fs = require("fs");
const path = require("path");
const app = express();
app.use(cors());
app.use(express.json());
const DB = path.join(__dirname, "database.json");
function load(){ try{return JSON.parse(fs.readFileSync(DB,"utf8"));}catch(e){return {alerts:[],telemetry:[]};}}
function save(x){fs.writeFileSync(DB,JSON.stringify(x,null,2));}

app.get("/api/health",(req,res)=>res.json({ok:true,service:"GeoSafe Server"}));
app.post("/api/telemetry",(req,res)=>{
  const db=load(); db.telemetry.push({...req.body,receivedAt:new Date().toISOString()});
  db.telemetry=db.telemetry.slice(-500); save(db); res.json({ok:true});
});
app.post("/api/alerts",(req,res)=>{
  const db=load(); const alert={id:Date.now(),...req.body,receivedAt:new Date().toISOString()};
  db.alerts.push(alert); save(db); res.json({ok:true,alert});
});
app.get("/api/alerts",(req,res)=>res.json(load().alerts.slice().reverse()));
app.get("/api/telemetry",(req,res)=>res.json(load().telemetry.slice(-100).reverse()));

app.use("/",express.static(path.join(__dirname,"guardian")));
app.listen(3000,()=>console.log("GeoSafe server: http://localhost:3000"));
