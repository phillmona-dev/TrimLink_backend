-- V13: Create haircut_styles table and seed it with premium style presets
CREATE TABLE IF NOT EXISTS haircut_styles (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    image_url TEXT NOT NULL,
    tags TEXT
);

-- Seed with 8 premium mock haircut styles
INSERT INTO haircut_styles (id, name, description, category, image_url, tags) VALUES
('style-buzz-cut', 'Classic Buzz Cut', 'A minimal, low-maintenance haircut with uniform length all over. Clean, sharp, and timeless.', 'Short', 'https://images.unsplash.com/photo-1605497746444-052d59fac596?w=600&auto=format&fit=crop&q=80', 'Low Maintenance,Classic,Short'),
('style-mid-fade', 'Mid Skin Fade', 'A gorgeous blend starting midway up the sides, tapering down to the skin. Modern and versatile.', 'Fade', 'https://images.unsplash.com/photo-1621605815971-fbc98d665033?w=600&auto=format&fit=crop&q=80', 'Sharp,Modern,Fade'),
('style-textured-crop', 'French Crop with Texture', 'French crop featuring textured fringe with high skin fade on the sides. Very modern and easy to style.', 'Modern', 'https://images.unsplash.com/photo-1599351431247-f13b283253b9?w=600&auto=format&fit=crop&q=80', 'Textured,Trendy,Fringe'),
('style-slick-back', 'Undercut Slick Back', 'High-contrast style with long slicked-back hair on top and short disconnected sides.', 'Classic', 'https://images.unsplash.com/photo-1503951914875-452162b0f3f1?w=600&auto=format&fit=crop&q=80', 'Slick,Retro,Undercut'),
('style-pompadour', 'Modern Pompadour', 'High-volume style swept upwards and backwards, paired with a soft taper fade on the sides.', 'Classic', 'https://images.unsplash.com/photo-1517832606299-7ae9b720a186?w=600&auto=format&fit=crop&q=80', 'Volume,Gentleman,Taper'),
('style-afro-taper', 'Afro Taper Fade', 'Celebrates natural curl volume on top while keeping the neck and temple lines clean and tapered.', 'Fade', 'https://images.unsplash.com/photo-1567894340315-735d7c361db0?w=600&auto=format&fit=crop&q=80', 'Curly,Natural,Ethiopian Fav'),
('style-quiff', 'Textured Quiff', 'Sporty, dynamic style brushed forward and up at the front, with a clean drop fade.', 'Modern', 'https://images.unsplash.com/photo-1622286342621-4bd786c2447c?w=600&auto=format&fit=crop&q=80', 'Textured,Casual,Fade'),
('style-side-part', 'Executive Side Part', 'A professional, clean-cut look with a defined side parting line. Perfect for formal wear.', 'Classic', 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=600&auto=format&fit=crop&q=80', 'Formal,Parted,Polished')
ON CONFLICT (id) DO NOTHING;
