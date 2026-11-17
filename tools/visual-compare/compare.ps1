param(
    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,
    [Parameter(Mandatory = $true)]
    [string]$CandidateDirectory
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$outDir = Join-Path $repo $OutputDirectory
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$targetSize = [System.Drawing.Size]::new(1080, 2400)
$labelHeight = 76
$font = [System.Drawing.Font]::new('Segoe UI', 24, [System.Drawing.FontStyle]::Bold)
$labelBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
$background = [System.Drawing.Color]::FromArgb(255, 18, 16, 28)

function Open-Bitmap([string]$Path) {
    $image = [System.Drawing.Image]::FromFile((Join-Path $repo $Path))
    $bitmap = [System.Drawing.Bitmap]::new($image)
    $image.Dispose()
    return $bitmap
}

function New-FittedBitmap([System.Drawing.Bitmap]$Source, [System.Drawing.Rectangle]$Crop) {
    $canvas = [System.Drawing.Bitmap]::new($targetSize.Width, $targetSize.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($canvas)
    $g.Clear($background)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $scale = [Math]::Min($targetSize.Width / [double]$Crop.Width, $targetSize.Height / [double]$Crop.Height)
    $width = [int][Math]::Round($Crop.Width * $scale)
    $height = [int][Math]::Round($Crop.Height * $scale)
    $x = [int][Math]::Floor(($targetSize.Width - $width) / 2)
    $y = [int][Math]::Floor(($targetSize.Height - $height) / 2)
    $destination = [System.Drawing.Rectangle]::new($x, $y, $width, $height)
    $g.DrawImage($Source, $destination, $Crop, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    return [pscustomobject]@{ Bitmap = $canvas; Scale = $scale; Destination = $destination }
}

function Add-Label([System.Drawing.Bitmap]$Image, [string]$Text) {
    $g = [System.Drawing.Graphics]::FromImage($Image)
    $g.FillRectangle([System.Drawing.SolidBrush]::new($background), 0, 0, $Image.Width, $labelHeight)
    $g.DrawString($Text, $font, $labelBrush, 18, 18)
    $g.Dispose()
}

function Save-Compare([string]$Name, [string]$TargetPath, [string]$ReferencePath, [System.Drawing.Rectangle]$ReferenceCrop, [string]$TargetLabel, [string]$ReferenceLabel) {
    $target = Open-Bitmap $TargetPath
    $reference = Open-Bitmap $ReferencePath
    try {
        $targetCrop = [System.Drawing.Rectangle]::new(0, 0, $target.Width, $target.Height)
        $targetFit = New-FittedBitmap $target $targetCrop
        $referenceFit = New-FittedBitmap $reference $ReferenceCrop

        $side = [System.Drawing.Bitmap]::new($targetSize.Width * 2, $targetSize.Height + $labelHeight, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($side)
        $g.Clear($background)
        $g.DrawImage($targetFit.Bitmap, 0, $labelHeight)
        $g.DrawImage($referenceFit.Bitmap, $targetSize.Width, $labelHeight)
        $g.Dispose()
        Add-Label $side "$TargetLabel  |  $($target.Width)×$($target.Height) px"
        $g = [System.Drawing.Graphics]::FromImage($side)
        $g.DrawString("$ReferenceLabel  |  crop $($ReferenceCrop.X),$($ReferenceCrop.Y) $($ReferenceCrop.Width)×$($ReferenceCrop.Height) px", $font, $labelBrush, $targetSize.Width + 18, 18)
        $g.Dispose()
        $side.Save((Join-Path $outDir "$Name-side-by-side.png"), [System.Drawing.Imaging.ImageFormat]::Png)

        $overlay = [System.Drawing.Bitmap]::new($targetSize.Width, $targetSize.Height + $labelHeight, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($overlay)
        $g.Clear($background)
        $g.DrawImage($targetFit.Bitmap, 0, $labelHeight)
        $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
        $matrix = [System.Drawing.Imaging.ColorMatrix]::new()
        $matrix.Matrix33 = 0.5
        $attributes.SetColorMatrix($matrix)
        $g.DrawImage($referenceFit.Bitmap, [System.Drawing.Rectangle]::new(0, $labelHeight, $targetSize.Width, $targetSize.Height), 0, 0, $targetSize.Width, $targetSize.Height, [System.Drawing.GraphicsUnit]::Pixel, $attributes)
        $g.Dispose()
        $attributes.Dispose()
        Add-Label $overlay "$Name  |  candidate + reference at 50% alpha"
        $overlay.Save((Join-Path $outDir "$Name-overlay-50.png"), [System.Drawing.Imaging.ImageFormat]::Png)

        [pscustomobject]@{
            name = $Name
            candidate = $TargetPath
            candidateSize = "$($target.Width)x$($target.Height)"
            reference = $ReferencePath
            referenceCrop = @{ x = $ReferenceCrop.X; y = $ReferenceCrop.Y; width = $ReferenceCrop.Width; height = $ReferenceCrop.Height }
            canvas = "$($targetSize.Width)x$($targetSize.Height)"
            candidateScale = [Math]::Round($targetFit.Scale, 6)
            referenceScale = [Math]::Round($referenceFit.Scale, 6)
            referencePlaced = @{ x = $referenceFit.Destination.X; y = $referenceFit.Destination.Y; width = $referenceFit.Destination.Width; height = $referenceFit.Destination.Height }
        }
    }
    finally {
        $target.Dispose()
        $reference.Dispose()
        $targetFit.Bitmap.Dispose()
        $referenceFit.Bitmap.Dispose()
        $side.Dispose()
        $overlay.Dispose()
    }
}

$candidateRoot = $CandidateDirectory
$results = @(
    Save-Compare 'C01-R02' "$candidateRoot/C01-home-device.png" 'design/Главная RepTrack_ фитнес-прогресс.png' ([System.Drawing.Rectangle]::new(0, 70, 841, 1760)) 'C01 · APK device capture' 'R02 · home reference'
    Save-Compare 'C03-R03' "$candidateRoot/C03-workout-device.png" 'design/Тёмный интерфейс тренировки RepTrack.png' ([System.Drawing.Rectangle]::new(0, 70, 841, 1760)) 'C03 · APK device capture' 'R03 · workout reference'
    Save-Compare 'C01-R01' "$candidateRoot/C01-home-device.png" 'design/Фиолетовый Pro_ концепт RepTrack.png' ([System.Drawing.Rectangle]::new(30, 178, 269, 552)) 'C01 · APK device capture' 'R01 · home phone'
    Save-Compare 'C03-R01' "$candidateRoot/C03-workout-device.png" 'design/Фиолетовый Pro_ концепт RepTrack.png' ([System.Drawing.Rectangle]::new(329, 178, 278, 552)) 'C03 · APK device capture' 'R01 · workout phone'
    Save-Compare 'C05-R04' "$candidateRoot/C05-approaches-device.png" 'design/Жим ногами — экран тренировки RepTrack.png' ([System.Drawing.Rectangle]::new(0, 70, 841, 1760)) 'C05 · APK exercise sets' 'R04 · exercise reference'
    Save-Compare 'C06-R04' "$candidateRoot/C06-technique-device.png" 'design/Жим ногами — экран тренировки RepTrack.png' ([System.Drawing.Rectangle]::new(0, 70, 841, 1760)) 'C06 · APK technique' 'R04 · exercise reference'
    Save-Compare 'C07-R04' "$candidateRoot/C07-muscles-device.png" 'design/Жим ногами — экран тренировки RepTrack.png' ([System.Drawing.Rectangle]::new(0, 70, 841, 1760)) 'C07 · APK muscles' 'R04 · exercise reference'
    Save-Compare 'C09-R06' "$candidateRoot/C09-plan-device.png" 'design/План тренировок RepTrack_ Push-цикл.png' ([System.Drawing.Rectangle]::new(0, 70, 841, 1760)) 'C09 · APK plan' 'R06 · plan reference'
    Save-Compare 'C11-R05' "$candidateRoot/C11-chart-device.png" 'design/Прогресс в жиме ногами — RepTrack.png' ([System.Drawing.Rectangle]::new(0, 70, 841, 1760)) 'C11 · APK progress' 'R05 · progress reference'
)
$results | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 (Join-Path $outDir 'geometry.json')

$font.Dispose()
$labelBrush.Dispose()
Write-Output "Wrote $($results.Count) side-by-side and $($results.Count) 50% overlay images to $outDir using $candidateRoot"
