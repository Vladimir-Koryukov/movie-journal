$ErrorActionPreference = 'Stop'

Push-Location -LiteralPath (Join-Path $PSScriptRoot 'frontend')

try {
    npm.cmd ci

    if ($LASTEXITCODE -ne 0) {
        throw 'Frontend dependency installation failed.'
    }

    npm.cmd run build

    if ($LASTEXITCODE -ne 0) {
        throw 'Frontend build failed.'
    }

    if (-not (Test-Path -LiteralPath '.\dist\index.html' -PathType Leaf)) {
        throw 'Frontend build did not produce dist/index.html.'
    }
}
finally {
    Pop-Location
}

Push-Location -LiteralPath (Join-Path $PSScriptRoot 'backend')

try {
    .\mvnw.cmd clean verify

    if ($LASTEXITCODE -ne 0) {
        throw 'Backend build or tests failed.'
    }

    if (-not (Test-Path -LiteralPath '.\target\classes\static\index.html' -PathType Leaf)) {
        throw 'Frontend files were not included in the backend build.'
    }
}
finally {
    Pop-Location
}

Write-Host 'Build completed. The application JAR is in backend/target.'