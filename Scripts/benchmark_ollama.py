import requests
import time
import sys
import argparse
import json

# Hardware-Specific Thresholds (RTX 2000 / 220GB RAM / 28-Core)
THRESHOLDS = {
    "ELITE": {"min_tps": 1.5, "max_ttft": 15.0},  # >70B models
    "STRONG": {"min_tps": 5.0, "max_ttft": 5.0},  # ~70B models
    "FAST": {"min_tps": 15.0, "max_ttft": 1.0}    # <20B models
}

def get_tier(model_name):
    name_lower = model_name.lower()
    for x in ["8x22b", "r-plus", "miqu", "180b", "2.5"]:
        if x in name_lower:
            return "ELITE"
    if "70b" in name_lower:
        return "STRONG"
    return "FAST"

def benchmark_model(model_name):
    url = "http://localhost:11434/api/generate"
    payload = {
        "model": model_name,
        "prompt": "Write a complex Kotlin coroutine example for a repository pattern.",
        "stream": False,
        "options": {"num_predict": 50}  # Reduced for faster dashboard updates
    }
    
    try:
        response = requests.post(url, json=payload, timeout=300)
        
        if response.status_code == 200:
            data = response.json()
            eval_duration = data.get("eval_duration", 0) / 1e9
            eval_count = data.get("eval_count", 0)
            prompt_eval_duration = data.get("prompt_eval_duration", 0) / 1e9
            
            tps = eval_count / eval_duration if eval_duration > 0 else 0
            ttft = prompt_eval_duration
            
            return {"model": model_name, "tps": tps, "ttft": ttft, "success": True}
        else:
            return {"model": model_name, "success": False, "error": f"Status {response.status_code}"}
    except Exception as e:
        return {"model": model_name, "success": False, "error": str(e)}

def run_suite(fitness_mode=False, json_mode=False):
    active_models = []
    try:
        models_resp = requests.get("http://localhost:11434/api/tags", timeout=5)
        if models_resp.status_code == 200:
            active_models = [m["name"] for m in models_resp.json().get("models", [])]
        else:
            if not json_mode: print(f"Error: Ollama API returned {models_resp.status_code}")
            sys.exit(1)
    except Exception as e:
        if not json_mode: print(f"Error: Could not connect to Ollama: {str(e)}")
        sys.exit(1)

    results = []
    unfit_models = []

    if not json_mode:
        print(f"\n--- Ollama Fitness Suite ({'FITNESS' if fitness_mode else 'BENCHMARK'}) ---")

    for model_name in active_models:
        if not json_mode:
            print(f"Testing {model_name}...", end=" ", flush=True)
        
        res = benchmark_model(model_name)
        if res and res.get("success"):
            base_name = model_name.split(":")[0]
            tier = get_tier(base_name)
            threshold = THRESHOLDS.get(tier, THRESHOLDS["FAST"])
            
            is_fit = res["tps"] >= threshold["min_tps"] and res["ttft"] <= threshold["max_ttft"]
            res["is_fit"] = is_fit
            res["tier"] = tier
            results.append(res)
            
            if not is_fit:
                unfit_models.append(base_name)
            
            if not json_mode:
                status_str = "PASSED" if is_fit else "UNFIT"
                print(f"TPS: {res['tps']:.2f} | TTFT: {res['ttft']:.2f}s | {status_str}")
        else:
            if not json_mode:
                print(f"FAILED: {res.get('error') if res else 'Unknown error'}")

    if json_mode:
        print(json.dumps({"results": results, "unfit": unfit_models}))
        sys.exit(0)

    print("\n--- PERFORMANCE REPORT ---")
    print(f"{'Model':<25} | {'TPS':<8} | {'TTFT':<8} | {'Status':<10}")
    print("-" * 65)
    for r in results:
        status = "PASSED" if r["is_fit"] else "UNFIT"
        print(f"{r['model']:<25} | {r['tps']:<8.2f} | {r['ttft']:<8.2f} | {status:<10}")
    
    if fitness_mode and unfit_models:
        print(f"\nCRITICAL: {len(unfit_models)} models failed fitness thresholds.")
        sys.exit(1)
    
    sys.exit(0)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--fitness", action="store_true", help="Exit with error if models are unfit")
    parser.add_argument("--json", action="store_true", help="Output results in JSON format")
    args = parser.parse_args()
    run_suite(fitness_mode=args.fitness, json_mode=args.json)
