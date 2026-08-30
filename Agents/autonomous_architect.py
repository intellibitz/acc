import os
import sys
from litellm import completion

# --- Agent Profile ---
AGENT_NAME = "Architect-Alpha"
# Default to a model served via LiteLLM or Ollama directly
DEFAULT_MODEL = os.getenv("ACC_MODEL", "ollama/phi3")
API_BASE = os.getenv("ACC_API_BASE", "http://localhost:11434") # Ollama default

class AutonomousArchitect:
    def __init__(self, model, api_base=None):
        self.model = model
        self.api_base = api_base
        self.history = []

    def think(self, prompt):
        print(f"[{AGENT_NAME}] Thinking (via LiteLLM)...")
        
        # Add to history
        self.history.append({"role": "user", "content": prompt})
        
        try:
            response = completion(
                model=self.model,
                messages=self.history,
                api_base=self.api_base
            )
            
            answer = response.choices[0].message.content
            self.history.append({"role": "assistant", "content": answer})
            return answer
            
        except Exception as e:
            return f"Exception: {str(e)}"

    def run_loop(self):
        print(f"--- {AGENT_NAME} ONLINE (LiteLLM Mode) ---")
        print(f"Targeting Model: {self.model}")
        print(f"API Base: {self.api_base if self.api_base else 'Default'}")
        
        while True:
            try:
                user_input = input("\n[Master] > ")
                if user_input.lower() in ["exit", "quit"]: break
                
                response = self.think(user_input)
                print(f"\n[{AGENT_NAME}]\n{response}")
            except KeyboardInterrupt:
                print("\nShutting down...")
                break

if __name__ == "__main__":
    # In a real ACC environment, we would detect if the proxy is running
    # For now, we default to the env vars or internal defaults
    model_name = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_MODEL
    api_base = sys.argv[2] if len(sys.argv) > 2 else API_BASE
    
    agent = AutonomousArchitect(model_name, api_base)
    agent.run_loop()
