# Test Images for EXIF Integration Tests

This directory should contain sample JPEG images with EXIF metadata for testing purposes.

## Required Test Images

### 1. `sample-with-exif.jpg`
A JPEG image containing basic EXIF metadata:
- Camera make and model
- Date/time original
- Image dimensions
- Orientation
- Optional: lens model, focal length, aperture, shutter speed, ISO

### 2. `sample-with-gps.jpg`
A JPEG image containing GPS EXIF data:
- Latitude
- Longitude
- Optional: altitude

## How to Add Test Images

You can use any of these methods:

### Option 1: Use Your Own Photos
Copy a photo from your camera or phone that contains EXIF data:
```bash
cp ~/Pictures/photo-with-exif.jpg sample-with-exif.jpg
cp ~/Pictures/photo-with-gps.jpg sample-with-gps.jpg
```

### Option 2: Download Sample Images
Many free stock photo sites provide images with EXIF data intact.

### Option 3: Create Test Images with exiftool
```bash
# Install exiftool
sudo apt-get install libimage-exiftool-perl

# Create a basic image and add EXIF data
convert -size 100x100 xc:blue sample.jpg
exiftool -Make="Canon" -Model="Canon EOS 5D" sample.jpg
exiftool -GPSLatitude="37.7749" -GPSLongitude="-122.4194" sample.jpg
```

## Privacy Note

If using personal photos, ensure they don't contain sensitive information.
Consider using photos that are already publicly shared or create synthetic test images.

## Test Behavior

If these images are not present, the corresponding tests will be skipped with informational messages.
The tests will still pass, but EXIF extraction won't be fully verified.
