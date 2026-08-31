# ==============================================================================
# acc WRAPPER - UNIFIED PYTHON ORCHESTRATOR (Windows)
# ==============================================================================

$PROJECT_ROOT = $PSScriptRoot
if ([string]::IsNullOrEmpty($PROJECT_ROOT)) { $PROJECT_ROOT = Get-Location }

# Ensure Python is installed
if (!(Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Host "[error] Python is required but not found in PATH." -ForegroundColor Red
    exit 1
}

# Delegate to unified python orchestrator
python "$PROJECT_ROOT\acc.py" $args
