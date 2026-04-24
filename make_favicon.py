from PIL import Image

def create_favicon(input_path, output_path):
    img = Image.open(input_path).convert("RGBA")
    data = img.getdata()
    
    new_data = []
    # Assume the top-left pixel is the background
    bg_color = data[0]
    threshold = 60 # Slightly higher threshold to catch near-blacks
    
    for item in data:
        dist = abs(item[0] - bg_color[0]) + abs(item[1] - bg_color[1]) + abs(item[2] - bg_color[2])
        if dist < threshold:
            new_data.append((item[0], item[1], item[2], 0))
        else:
            new_data.append(item)
            
    img.putdata(new_data)
    
    # Crop to bounding box of non-transparent pixels
    bbox = img.getbbox()
    if bbox:
        img = img.crop(bbox)
        
    img.thumbnail((256, 256))
    img.save(output_path, "PNG")

create_favicon("public/logo.png", "public/favicon.png")
