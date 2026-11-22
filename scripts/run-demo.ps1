$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
New-Item -ItemType Directory -Path out -Force | Out-Null
$files = Get-ChildItem -Path src/main/java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 11 -d out @files
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp out os.App @args
exit $LASTEXITCODE
