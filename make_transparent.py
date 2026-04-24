from PIL import Image

def extract_glow(input_path, output_path, crop=False):
    img = Image.open(input_path).convert("RGBA")
    data = img.getdata()
    
    new_data = []
    
    # Sample the background color from the top-left pixel
    bg_r, bg_g, bg_b = data[0][:3]
    
    for r, g, b, a in data:
        # Subtract background to find the 'added' light
        nr = max(0, r - bg_r)
        ng = max(0, g - bg_g)
        nb = max(0, b - bg_b)
        
        # Alpha is the maximum difference from background
        alpha = max(nr, ng, nb)
        
        if alpha == 0:
            new_data.append((0, 0, 0, 0))
        else:
            # Un-premultiply the color
            un_r = min(255, int((nr / alpha) * 255))
            un_g = min(255, int((ng / alpha) * 255))
            un_b = min(255, int((nb / alpha) * 255))
            
            # Boost alpha slightly to retain glow intensity
            final_alpha = min(255, int((alpha / 255.0) ** 0.8 * 255))
            
            new_data.append((un_r, un_g, un_b, final_alpha))
            
    img.putdata(new_data)
    
    if crop:
        bbox = img.getbbox()
        if bbox:
            img = img.crop(bbox)
        img.thumbnail((256, 256))
        
    img.save(output_path, "PNG")

# Re-process the original logo image to get a perfectly transparent one
# Process the new uploaded logo
input_img = r"C:\Users\jpitt\.gemini\antigravity\brain\31cff9e4-5053-4307-b8c0-6356f13c2c5d\media__1777048029004.jpg"
extract_glow(input_img, "public/favicon.png", crop=True)
extract_glow(input_img, "public/logo.png", crop=False)
