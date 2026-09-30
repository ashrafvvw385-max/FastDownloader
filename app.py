import os
import requests
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

app = FastAPI(title="FastDownloader API")

API_TOKEN = os.environ.get("API_TOKEN", "")
ARIA2_SECRET = os.environ.get("ARIA2_SECRET", "")
ARIA2_URL = "http://aria2:6800/jsonrpc"

class AddRequest(BaseModel):
    url: str

def auth(authorization: str | None):
    if not API_TOKEN or authorization != f"Bearer {API_TOKEN}":
        raise HTTPException(status_code=401, detail="Unauthorized")

def rpc(method: str, params=None):
    params = params or []
    payload = {
        "jsonrpc": "2.0",
        "id": "fastdownloader",
        "method": method,
        "params": [f"token:{ARIA2_SECRET}"] + params,
    }
    r = requests.post(ARIA2_URL, json=payload, timeout=15)
    r.raise_for_status()
    data = r.json()
    if "error" in data:
        raise HTTPException(status_code=400, detail=data["error"])
    return data["result"]

@app.get("/health")
def health():
    return {"ok": True}

@app.get("/api/downloads")
def downloads(authorization: str | None = Header(default=None)):
    auth(authorization)
    active = rpc("aria2.tellActive", [[
        "gid", "status", "totalLength", "completedLength",
        "downloadSpeed", "files"
    ]])
    waiting = rpc("aria2.tellWaiting", [0, 100, [
        "gid", "status", "totalLength", "completedLength",
        "downloadSpeed", "files"
    ]])
    stopped = rpc("aria2.tellStopped", [0, 100, [
        "gid", "status", "totalLength", "completedLength",
        "downloadSpeed", "files"
    ]])
    return {"active": active, "waiting": waiting, "stopped": stopped}

@app.post("/api/downloads")
def add(req: AddRequest, authorization: str | None = Header(default=None)):
    auth(authorization)
    if not req.url.startswith(("http://", "https://")):
        raise HTTPException(status_code=400, detail="Only HTTP(S) URLs are supported")
    options = {
        "dir": "/downloads",
        "continue": "true",
        "always-resume": "true",
        "split": os.getenv("SPLIT", "16"),
        "max-connection-per-server": os.getenv("MAX_CONNECTIONS", "16"),
        "min-split-size": os.getenv("MIN_SPLIT_SIZE", "4M"),
        "max-concurrent-downloads": os.getenv("MAX_CONCURRENT_DOWNLOADS", "3"),
        "file-allocation": "none",
        "auto-file-renaming": "true",
    }
    gid = rpc("aria2.addUri", [[req.url], options])
    return {"gid": gid}

@app.post("/api/pause/{gid}")
def pause(gid: str, authorization: str | None = Header(default=None)):
    auth(authorization)
    return {"result": rpc("aria2.pause", [gid])}

@app.post("/api/resume/{gid}")
def resume(gid: str, authorization: str | None = Header(default=None)):
    auth(authorization)
    return {"result": rpc("aria2.unpause", [gid])}

@app.delete("/api/downloads/{gid}")
def remove(gid: str, authorization: str | None = Header(default=None)):
    auth(authorization)
    return {"result": rpc("aria2.removeDownloadResult", [gid])}

@app.get("/api/files")
def files(authorization: str | None = Header(default=None)):
    auth(authorization)
    items = []
    root = "/downloads"
    if os.path.isdir(root):
        for name in sorted(os.listdir(root)):
            path = os.path.join(root, name)
            if os.path.isfile(path):
                items.append({
                    "name": name,
                    "size": os.path.getsize(path),
                    "url": f"/downloads/{name}",
                })
    return {"files": items}
