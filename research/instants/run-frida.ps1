# run-frida.ps1 — lance la traque du gate galerie Instants.
# Prérequis : appareil rooté connecté + frida-server 17.15.0 en cours dessus
# (voir WORKFLOW.md §1-§2).

$ErrorActionPreference = "Stop"
$adb    = "C:\Users\Administrator\IdeaProjects\.tools\platform-tools\adb.exe"
$script = Join-Path $PSScriptRoot "frida-instants-gallery.js"
$pkg    = "com.instagram.android"

Write-Host "== Appareils ADB ==" -ForegroundColor Cyan
& $adb devices

Write-Host "`n== frida-server tourne-t-il ? ==" -ForegroundColor Cyan
$ps = frida-ps -U 2>&1 | Select-String -Pattern "instagram", "frida"
if (-not $ps) {
  Write-Warning "frida-ps ne répond pas. Démarre frida-server sur l'appareil :"
  Write-Host '  & $adb shell "/data/local/tmp/frida-server &"'
}

Write-Host "`n== Lancement (spawn $pkg) ==" -ForegroundColor Cyan
Write-Host "Ouvre la caméra Instants dans l'app, puis observe la console." -ForegroundColor Yellow
frida -U -f $pkg -l $script
