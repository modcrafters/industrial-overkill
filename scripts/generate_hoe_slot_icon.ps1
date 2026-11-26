# Muted native 16px empty-slot hint. Each character is one pixel; no resizing.
Add-Type -AssemblyName System.Drawing
$repoRoot = Split-Path -Parent $PSScriptRoot
$target = Join-Path $repoRoot 'src/main/resources/assets/industrial_overkill/textures/gui/sprites/slot_hoe.png'
$rows = @(
    '......SS....',
    '.....SIIS...',
    '....SIIIIS..',
    '...SIISSIIS.',
    '...SS...SIS.',
    '.......SIS..',
    '......SIS...',
    '.....SIS....',
    '....SIS.....',
    '...SIS......',
    '..SIS.......',
    '..SS........'
)
$palette = @{
    S = [System.Drawing.ColorTranslator]::FromHtml('#5B605C')
    I = [System.Drawing.ColorTranslator]::FromHtml('#A6ACA5')
}
$sprite = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
try {
    for ($y = 0; $y -lt $rows.Count; $y++) {
        if ($rows[$y].Length -ne 12) { throw "Expected 12 columns in row $y" }
        for ($x = 0; $x -lt 12; $x++) {
            $pixel = [string]$rows[$y][$x]
            if ($pixel -eq '.') { continue }
            $sprite.SetPixel($x + 2, $y + 2, $palette[$pixel])
        }
    }
    $sprite.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $sprite.Dispose() }
