# Native pixel art: each character is exactly one output pixel. No resizing.
Add-Type -AssemblyName System.Drawing
$repoRoot = Split-Path -Parent $PSScriptRoot
$spriteDirectory = Join-Path $repoRoot 'src/main/resources/assets/industrial_overkill/textures/gui/sprites'
$palette = @{
    D = '#293B29'; G = '#548735'; L = '#A0D45C'
    Y = '#E3AE43'; H = '#FFE59A'; C = '#FFF2CD'
    B = '#805033'; T = '#BB8051'
}
$icons = [ordered]@{
    planting_first_found = @(
        '......D..D..',
        '.....DLDDLD.',
        '......DGGLD.',
        '.......DGD..',
        '........GD..',
        '.......DDDD.',
        '..D....DYHD.',
        '..DHD..DYYD.',
        'DDCHHD.DYYD.',
        'DCCCHHD.DD..',
        'DDCHHD......',
        '..DDD.......'
    )
    planting_round_robin = @(
        '...DDDDD....',
        '..DGGGGLD...',
        '.DGD...DLD..',
        '.DGD..DDLLD.',
        '.DGDYH.DDD..',
        '....YY......',
        '......HY....',
        '..DDD.YYDGD.',
        '.DLLDD..DGD.',
        '..DLD...DGD.',
        '...DLGGGGD..',
        '....DDDDD...'
    )
    planting_rows = @(
        'L.G.L.G.L.G.',
        '.G...G...G..',
        'DBTTBTTBTTBD',
        '............',
        'L.G.L.G.L.G.',
        '.G...G...G..',
        'DBTTBTTBTTBD',
        '............',
        'L.G.L.G.L.G.',
        '.G...G...G..',
        'DBTTBTTBTTBD',
        '............'
    )
}
foreach ($entry in $icons.GetEnumerator()) {
    $sprite = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        if ($entry.Value.Count -ne 12) { throw "Expected 12 rows: $($entry.Key)" }
        for ($y = 0; $y -lt 12; $y++) {
            $row = $entry.Value[$y]
            if ($row.Length -ne 12) { throw "Expected 12 columns: $($entry.Key), row $y" }
            for ($x = 0; $x -lt 12; $x++) {
                $pixel = [string]$row[$x]
                if ($pixel -eq '.') { continue }
                if (!$palette.ContainsKey($pixel)) { throw "Unknown palette entry: $pixel" }
                $sprite.SetPixel($x + 2, $y + 2, [System.Drawing.ColorTranslator]::FromHtml($palette[$pixel]))
            }
        }
        $sprite.Save((Join-Path $spriteDirectory ($entry.Key + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $sprite.Dispose() }
}
