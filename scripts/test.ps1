$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
New-Item -ItemType Directory -Path out/main -Force | Out-Null
New-Item -ItemType Directory -Path out/test -Force | Out-Null
$files = Get-ChildItem -Path src/main/java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 11 -d out/main @files
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& javac --release 11 -cp out/main -d out/test src/test/java/os/RegressionSuite.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp 'out/main;out/test' os.RegressionSuite
exit $LASTEXITCODE
