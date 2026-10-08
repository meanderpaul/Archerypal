# Validates the RPI-backup Play SA against the identity this build expects.
# Does not copy or print private key material.

$ErrorActionPreference = "Stop"

$expectedEmail = "archerypal-play-verify@archerypal.iam.gserviceaccount.com"
$expectedProject = "archerypal"
$candidates = @(
    (Join-Path $PSScriptRoot "..\secrets\google-play-sa.json"),
    "D:\RPIserver-backup-20260818\guest-archerypal\etc\archerypal\google-play-sa.json",
    "D:\RPIserver-backup-20260818\archerypal\etc\archerypal\google-play-sa.json"
)

$path = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $path) {
    Write-Error "No google-play-sa.json found under D:\RPIserver-backup-20260818\...\etc\archerypal\"
}

$j = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
$ok = $true
function Assert-Eq($label, $actual, $expected) {
    if ($actual -ne $expected) {
        Write-Host ("FAIL {0}: got '{1}' expected '{2}'" -f $label, $actual, $expected)
        $script:ok = $false
    } else {
        Write-Host ("OK   {0}={1}" -f $label, $actual)
    }
}

Write-Host "source=$path"
Assert-Eq "type" $j.type "service_account"
Assert-Eq "project_id" $j.project_id $expectedProject
Assert-Eq "client_email" $j.client_email $expectedEmail
if (-not $j.private_key) {
    Write-Host "FAIL private_key missing"
    $ok = $false
} else {
    Write-Host "OK   private_key=present (not shown)"
}

Write-Host ""
Write-Host "Current-build use: upload THIS file in RevenueCat -> Archerypal Android service credentials."
Write-Host "Do not commit it; do not put it under app/. Workers do not need this file."

if (-not $ok) { exit 1 }
