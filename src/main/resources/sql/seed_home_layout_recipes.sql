-- Migration: seed the 16 featured recipes referenced by home_layout.json
-- These UUIDs are hardcoded in the SDUI home layout. They must exist in the
-- DB as PUBLIC so any authenticated user can bookmark them.
-- Creator: test1 / alice (1ff5c5d2-...) — a stable seed user.
-- Safe to re-run: ON CONFLICT DO NOTHING.

INSERT INTO recipes (
    uuid, title, description,
    image_url, image_url_thumbnail,
    prep_time_minutes, cook_time_minutes, servings,
    creator_id, recipe_external_url,
    privacy, updated_at, deleted_at, server_updated_at
) VALUES
  ('a1b2c3d4-0000-0000-0000-000000000001', 'Paella',                  'Classic Spanish seafood and rice dish.',        'https://picsum.photos/seed/chefai-home-1/1024/768',  'https://picsum.photos/seed/chefai-home-1/320/240',  20, 40, 4, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 1, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000002', 'Grilled Chicken',         'Juicy grilled chicken breast with herbs.',      'https://picsum.photos/seed/chefai-home-2/1024/768',  'https://picsum.photos/seed/chefai-home-2/320/240',  10, 20, 2, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 2, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000003', 'Thai Green Curry',        'Aromatic Thai curry with coconut milk.',         'https://picsum.photos/seed/chefai-home-3/1024/768',  'https://picsum.photos/seed/chefai-home-3/320/240',  15, 25, 3, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 3, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000004', 'Margherita Pizza',        'Traditional Neapolitan pizza.',                  'https://picsum.photos/seed/chefai-home-4/1024/768',  'https://picsum.photos/seed/chefai-home-4/320/240',  20, 15, 2, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 4, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000005', 'Beef Stew',               'Hearty slow-cooked beef and vegetable stew.',   'https://picsum.photos/seed/chefai-home-5/1024/768',  'https://picsum.photos/seed/chefai-home-5/320/240',  20, 90, 6, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 5, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000006', 'Carbonara',               'Creamy Italian pasta with egg and guanciale.',  'https://picsum.photos/seed/chefai-home-6/1024/768',  'https://picsum.photos/seed/chefai-home-6/320/240',  10, 15, 2, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 6, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000007', 'Lasagna',                 'Layered pasta with meat sauce and béchamel.',   'https://picsum.photos/seed/chefai-home-7/1024/768',  'https://picsum.photos/seed/chefai-home-7/320/240',  30, 60, 6, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 7, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000008', 'Tuscan Sausage Pasta',    'Rich pasta with sausage and sun-dried tomato.',  'https://picsum.photos/seed/chefai-home-8/1024/768',  'https://picsum.photos/seed/chefai-home-8/320/240',  10, 20, 3, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 8, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000009', 'Salmon Teriyaki',         'Glazed salmon with teriyaki sauce.',             'https://picsum.photos/seed/chefai-home-9/1024/768',  'https://picsum.photos/seed/chefai-home-9/320/240',  10, 15, 2, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 9, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000010', 'Sushi Nigiris',           'Hand-pressed sushi with fresh fish.',            'https://picsum.photos/seed/chefai-home-10/1024/768', 'https://picsum.photos/seed/chefai-home-10/320/240', 30,  0, 4, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 10, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000011', 'Shrimp Ceviche',          'Fresh shrimp cured in citrus juice.',            'https://picsum.photos/seed/chefai-home-11/1024/768', 'https://picsum.photos/seed/chefai-home-11/320/240', 20,  0, 3, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 11, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000012', 'Mediterranean Chicken',   'Grilled chicken with olives and feta.',          'https://picsum.photos/seed/chefai-home-12/1024/768', 'https://picsum.photos/seed/chefai-home-12/320/240', 15, 25, 3, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 12, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000013', 'Battered Cod',            'Crispy beer-battered cod fillets.',              'https://picsum.photos/seed/chefai-home-13/1024/768', 'https://picsum.photos/seed/chefai-home-13/320/240', 15, 10, 2, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 13, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000014', 'Chocolate Chip Cookies',  'Classic soft-baked cookies.',                   'https://picsum.photos/seed/chefai-home-14/1024/768', 'https://picsum.photos/seed/chefai-home-14/320/240', 15, 12, 24, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 14, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000015', 'Blueberry Cheesecake',    'Creamy cheesecake with blueberry topping.',      'https://picsum.photos/seed/chefai-home-15/1024/768', 'https://picsum.photos/seed/chefai-home-15/320/240', 30, 60, 8, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 15, NULL, now()),
  ('a1b2c3d4-0000-0000-0000-000000000016', 'Ghirardelli Cookies',     'Rich double-chocolate cookies.',                 'https://picsum.photos/seed/chefai-home-16/1024/768', 'https://picsum.photos/seed/chefai-home-16/320/240', 20, 11, 18, '1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7', NULL, 'PUBLIC', 16, NULL, now())
ON CONFLICT (uuid) DO UPDATE SET
    privacy           = 'PUBLIC',
    server_updated_at = now();
