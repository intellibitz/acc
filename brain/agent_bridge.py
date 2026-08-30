import asyncio
import websockets
import json
import time
import os
from autonomous_architect import AutonomousArchitect

# --- Configuration ---
AGENT_NAME = "Architect-Alpha"
GATEWAY_URL = "ws://localhost:8080/ws/agent"
MODEL = os.getenv("ACC_MODEL", "ollama/phi3")
API_BASE = os.getenv("ACC_API_BASE", "http://localhost:11434")

class AgentBridge:
    def __init__(self):
        self.architect = AutonomousArchitect(MODEL, API_BASE)

    async def send_status(self, websocket, status, content="", thoughts=None, actions=None):
        message = {
            "agentName": AGENT_NAME,
            "status": status,
            "content": content,
            "thoughts": thoughts or [],
            "actions": actions or [],
            "metadata": {"timestamp": str(time.time())}
        }
        await websocket.send(json.dumps(message))

    async def run(self):
        print(f"Connecting to Acc Gateway at {GATEWAY_URL}...")
        try:
            async with websockets.connect(GATEWAY_URL) as websocket:
                print("Connected to Gateway.")
                await self.send_status(websocket, "IDLE", "Architect is ready.")

                while True:
                    # In a real scenario, we might wait for a command from the UI
                    # For this bridge demo, we'll take local input and stream the thinking
                    user_input = input("\n[Master] > ")
                    if user_input.lower() in ["exit", "quit"]: break

                    # 1. Start Thinking
                    await self.send_status(websocket, "THINKING", "Analyzing request...")
                    
                    # 2. Simulate Thought Process
                    thoughts = [
                        {"summary": "Parsing user intent", "timestamp": int(time.time() * 1000)},
                        {"summary": "Querying LiteLLM", "detail": f"Model: {MODEL}", "timestamp": int(time.time() * 1000)}
                    ]
                    await self.send_status(websocket, "THINKING", "Architect is formulating a response...", thoughts=thoughts)

                    # 3. Simulate Tool Usage (Executing)
                    actions = [
                        {"tool": "ls", "input": "-la", "result": "total 40\ndrwxr-xr-x  14 ramadoss  staff   448 Aug 30 21:51 ."}
                    ]
                    await self.send_status(websocket, "EXECUTING", "Running shell command...", thoughts=thoughts, actions=actions)
                    await asyncio.sleep(1) # Simulate execution time

                    # 4. Call the actual model
                    response = self.architect.think(user_input)
                    
                    # 5. Final Response
                    await self.send_status(websocket, "DONE", response, thoughts=thoughts, actions=actions)

        except Exception as e:
            print(f"Bridge Error: {e}")

if __name__ == "__main__":
    bridge = AgentBridge()
    asyncio.run(bridge.run())
