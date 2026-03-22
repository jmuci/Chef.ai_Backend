-- Sample Data Inserts for ChefAI Database (with valid hex UUIDs)
-- NOTE: For local/dev usage. Adjust for production compliance.

-- ===============================
-- USERS
-- ===============================
INSERT INTO users (uuid, user_name, display_name, email, password_hash, avatar_url, created_at, updated_at) VALUES
  ('10000000-0000-0000-0000-000000000001','alice','Alice','alice@example.com','hash1','https://example.com/av1.png',now(),now()),
  ('10000000-0000-0000-0000-000000000002','bob','Bob','bob@example.com','hash2','https://example.com/av2.png',now(),now()),
  ('10000000-0000-0000-0000-000000000003','carol','Carol','carol@example.com','hash3','https://example.com/av3.png',now(),now()),
  ('1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7','test1','Test 1','lorz88@gmail.com','$2a$12$YQoHYaun8kHxhxLc7ZoRrORwUFauuL5xONAHlwxQhDBb7t52LyHNC','https://example.com/av-test1.png','2026-01-12 15:57:29.03656','2026-01-12 15:57:29.036566'),
  ('29002be5-01a8-49d8-b2bd-25ad73745d13','testuser2','Test Two','test2@ex.com','$2a$12$/xxh8lXULJoVRl5/DJQZHu6Pw5ShEHw5oRGXutbVhBMWFPdrEnJ9m','https://example.com/av-test2.png','2026-02-23 20:11:22.607604','2026-02-23 20:11:22.607608'),
  ('d3c3a63a-0705-472a-a97f-d5edfde8aaa1','testUser3',NULL,'test3@ex.com','$2a$12$lQF9lESiEdXg9LggQWkaNuIOl3eZ3E4hsE/RReaijin0vZHMCThBW','https://example.com/av-test3.png','2026-02-23 21:52:44.828985','2026-02-23 21:52:44.828993'),
  ('d7773142-d89f-495a-9dda-aa4cd0a2ea84','TestUser4',NULL,'test4@ex.com','$2a$12$g67z8CUsrbXYyexEKI.LfuDg4t9j1S0o.FOnlTpsA.aCYndXdFtOW','https://example.com/av-test4.png','2026-02-24 00:52:55.059899','2026-02-24 00:52:55.059902'),
  ('e69845ff-5a3d-49f8-9ed4-f0f956ec8779','TestUser',NULL,'test1@ex.com','$2a$12$U7VAySUMasBHu9JUFcVbe.HxB2c81bJIdB9MyvnA9Z2C90avDKsxm','https://example.com/av-test5.png','2026-02-23 20:07:53.594713','2026-02-23 20:07:53.594717');

-- ===============================
-- ALLERGENS
-- ===============================
INSERT INTO allergens (uuid, display_name, updated_at, deleted_at, server_updated_at) VALUES
  ('20000000-0000-0000-0000-000000000001', 'Peanuts', 1, NULL, now()),
  ('20000000-0000-0000-0000-000000000002', 'Soy', 2, NULL, now()),
  ('20000000-0000-0000-0000-000000000003', 'Dairy', 3, NULL, now()),
  ('20000000-0000-0000-0000-000000000004', 'Gluten', 4, NULL, now()),
  ('20000000-0000-0000-0000-000000000005', 'Eggs', 5, NULL, now()),
  ('20000000-0000-0000-0000-000000000006', 'Fish', 6, NULL, now()),
  ('20000000-0000-0000-0000-000000000007', 'Shellfish', 7, NULL, now()),
  ('20000000-0000-0000-0000-000000000008', 'Tree nuts', 8, NULL, now()),
  ('20000000-0000-0000-0000-000000000009', 'Sesame', 9, NULL, now()),
  ('20000000-0000-0000-0000-00000000000a', 'Mustard', 10, NULL, now());

-- ===============================
-- SOURCE_CLASSIFICATIONS
-- ===============================
INSERT INTO source_classifications (uuid, category, subcategory, updated_at, deleted_at, server_updated_at) VALUES
  ('30000000-0000-0000-0000-000000000001', 'Vegetable', 'Root', 1, NULL, now()),
  ('30000000-0000-0000-0000-000000000002', 'Vegetable', 'Leafy', 2, NULL, now()),
  ('30000000-0000-0000-0000-000000000003', 'Fruit', NULL, 3, NULL, now()),
  ('30000000-0000-0000-0000-000000000004', 'Grain', 'Whole', 4, NULL, now()),
  ('30000000-0000-0000-0000-000000000005', 'Dairy', 'Milk', 5, NULL, now()),
  ('30000000-0000-0000-0000-000000000006', 'Meat', 'Red', 6, NULL, now()),
  ('30000000-0000-0000-0000-000000000007', 'Meat', 'White', 7, NULL, now()),
  ('30000000-0000-0000-0000-000000000008', 'Fish', NULL, 8, NULL, now()),
  ('30000000-0000-0000-0000-000000000009', 'Seafood', NULL, 9, NULL, now()),
  ('30000000-0000-0000-0000-00000000000a', 'Legume', NULL, 10, NULL, now());

-- ===============================
-- LABELS
-- ===============================
INSERT INTO labels (uuid, display_name, updated_at, deleted_at, server_updated_at) VALUES
  ('40000000-0000-0000-0000-000000000001', 'Vegan', 1, NULL, now()),
  ('40000000-0000-0000-0000-000000000002', 'Vegetarian', 2, NULL, now()),
  ('40000000-0000-0000-0000-000000000003', 'Gluten-Free', 3, NULL, now()),
  ('40000000-0000-0000-0000-000000000004', 'Dairy-Free', 4, NULL, now()),
  ('40000000-0000-0000-0000-000000000005', 'Nut-Free', 5, NULL, now()),
  ('40000000-0000-0000-0000-000000000006', 'High Protein', 6, NULL, now()),
  ('40000000-0000-0000-0000-000000000007', 'Low Carb', 7, NULL, now()),
  ('40000000-0000-0000-0000-000000000008', 'Paleo', 8, NULL, now()),
  ('40000000-0000-0000-0000-000000000009', 'Keto', 9, NULL, now()),
  ('40000000-0000-0000-0000-00000000000a', 'Contains Seafood', 10, NULL, now());

-- ===============================
-- TAGS
-- ===============================
INSERT INTO tags (uuid, display_name, updated_at, deleted_at, server_updated_at) VALUES
  ('50000000-0000-0000-0000-000000000001', 'Spicy', 1, NULL, now()),
  ('50000000-0000-0000-0000-000000000002', 'Sweet', 2, NULL, now()),
  ('50000000-0000-0000-0000-000000000003', 'Savory', 3, NULL, now()),
  ('50000000-0000-0000-0000-000000000004', 'Breakfast', 4, NULL, now()),
  ('50000000-0000-0000-0000-000000000005', 'Lunch', 5, NULL, now()),
  ('50000000-0000-0000-0000-000000000006', 'Dinner', 6, NULL, now()),
  ('50000000-0000-0000-0000-000000000007', 'Snack', 7, NULL, now()),
  ('50000000-0000-0000-0000-000000000008', 'Dessert', 8, NULL, now()),
  ('50000000-0000-0000-0000-000000000009', 'Holiday', 9, NULL, now()),
  ('50000000-0000-0000-0000-00000000000a', 'Quick', 10, NULL, now());

-- ===============================
-- INGREDIENTS
-- ===============================
INSERT INTO ingredients (uuid, display_name, allergen_id, source_primary_id, updated_at, deleted_at, server_updated_at) VALUES
  ('60000000-0000-0000-0000-000000000001','Flour','20000000-0000-0000-0000-000000000004','30000000-0000-0000-0000-000000000004',1,NULL,now()),
  ('60000000-0000-0000-0000-000000000002','Eggs','20000000-0000-0000-0000-000000000005','30000000-0000-0000-0000-000000000005',2,NULL,now()),
  ('60000000-0000-0000-0000-000000000003','Milk','20000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000005',3,NULL,now()),
  ('60000000-0000-0000-0000-000000000004','Chicken',NULL,'30000000-0000-0000-0000-000000000007',4,NULL,now()),
  ('60000000-0000-0000-0000-000000000005','Tomato',NULL,'30000000-0000-0000-0000-000000000002',5,NULL,now()),
  ('60000000-0000-0000-0000-000000000006','Spinach',NULL,'30000000-0000-0000-0000-000000000002',6,NULL,now()),
  ('60000000-0000-0000-0000-000000000007','Cheese','20000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000005',7,NULL,now()),
  ('60000000-0000-0000-0000-000000000008','Almond','20000000-0000-0000-0000-000000000008','30000000-0000-0000-0000-00000000000a',8,NULL,now()),
  ('60000000-0000-0000-0000-000000000009','Salmon','20000000-0000-0000-0000-000000000006','30000000-0000-0000-0000-000000000008',9,NULL,now()),
  ('60000000-0000-0000-0000-00000000000a','Rice',NULL,'30000000-0000-0000-0000-000000000004',10,NULL,now()),
  ('60000000-0000-0000-0000-00000000000b','Garlic',NULL,'30000000-0000-0000-0000-000000000001',11,NULL,now()),
  ('60000000-0000-0000-0000-00000000000c','Onion',NULL,'30000000-0000-0000-0000-000000000001',12,NULL,now()),
  ('60000000-0000-0000-0000-00000000000d','Bell Pepper',NULL,'30000000-0000-0000-0000-000000000002',13,NULL,now()),
  ('60000000-0000-0000-0000-00000000000e','Broccoli',NULL,'30000000-0000-0000-0000-000000000002',14,NULL,now()),
  ('60000000-0000-0000-0000-00000000000f','Beef',NULL,'30000000-0000-0000-0000-000000000006',15,NULL,now()),
  ('60000000-0000-0000-0000-000000000010','Tofu','20000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-00000000000a',16,NULL,now()),
  ('60000000-0000-0000-0000-000000000011','Shrimp','20000000-0000-0000-0000-000000000007','30000000-0000-0000-0000-000000000009',17,NULL,now()),
  ('60000000-0000-0000-0000-000000000012','Yogurt','20000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000005',18,NULL,now()),
  ('60000000-0000-0000-0000-000000000013','Oats',NULL,'30000000-0000-0000-0000-000000000004',19,NULL,now()),
  ('60000000-0000-0000-0000-000000000014','Lentils',NULL,'30000000-0000-0000-0000-00000000000a',20,NULL,now());

-- ===============================
-- RECIPES
-- ===============================
INSERT INTO recipes (uuid, title, description, image_url, image_url_thumbnail, prep_time_minutes, cook_time_minutes, servings, creator_id, recipe_external_url, privacy, updated_at, deleted_at, server_updated_at) VALUES
  ('70000000-0000-0000-0000-000000000001','Salmon Rice Bowl','Fresh salmon and rice.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',10,15,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'public',1,NULL,now()),
  ('70000000-0000-0000-0000-000000000002','Chicken Salad','Healthy chicken salad.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',10,20,1,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'public',2,NULL,now()),
  ('70000000-0000-0000-0000-000000000003','Tomato Soup','Tomato-based soup.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',10,30,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'public',3,NULL,now()),
  ('70000000-0000-0000-0000-000000000004','Vegan Stir Fry','Colorful veggie stir fry.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',15,10,3,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'public',4,NULL,now()),
  ('70000000-0000-0000-0000-000000000005','Cheese Omelette','Classic cheese omelette.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',5,5,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'private',5,NULL,now()),
  ('70000000-0000-0000-0000-000000000006','Nutty Snack','Snack with nuts.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',3,0,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'public',6,NULL,now()),
  ('70000000-0000-0000-0000-000000000007','Rice Pudding','Sweet rice pudding.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',10,25,3,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'public',7,NULL,now()),
  ('70000000-0000-0000-0000-000000000008','Almond Cookie','Cookies with almond.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',20,30,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'public',8,NULL,now()),
  ('70000000-0000-0000-0000-000000000009','Spinach Lasagna','Vegetarian lasagna.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',20,60,5,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'private',9,NULL,now()),
  ('70000000-0000-0000-0000-00000000000a','Egg Fried Rice','Classic egg and rice.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',10,15,2,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'public',10,NULL,now()),
  ('70000000-0000-0000-0000-00000000000b','Garlic Chicken Skillet','One-pan garlic chicken with peppers.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',12,18,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'public',11,NULL,now()),
  ('70000000-0000-0000-0000-00000000000c','Tofu Veggie Bowl','Tofu with broccoli and rice.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',15,12,2,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'public',12,NULL,now()),
  ('70000000-0000-0000-0000-00000000000d','Beef Pepper Stir Fry','Beef strips with bell peppers and onion.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',10,14,3,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'public',13,NULL,now()),
  ('70000000-0000-0000-0000-00000000000e','Shrimp Tomato Pasta','Light shrimp and tomato pasta-style dish.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',10,16,2,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'public',14,NULL,now()),
  ('70000000-0000-0000-0000-00000000000f','Savory Oats','Creamy oats with spinach and cheese.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',5,8,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'private',15,NULL,now()),
  ('70000000-0000-0000-0000-000000000010','Lentil Tomato Stew','Protein-rich lentil stew.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',12,30,4,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'public',16,NULL,now()),
  ('70000000-0000-0000-0000-000000000011','Yogurt Fruit Bowl','Chilled yogurt bowl with toppings.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',5,0,1,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'public',17,NULL,now()),
  ('70000000-0000-0000-0000-000000000012','Broccoli Rice Bake','Baked rice casserole with broccoli.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',15,25,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'public',18,NULL,now()),
  ('70000000-0000-0000-0000-000000000013','Onion Omelette Wrap','Egg wrap with onion and tomato.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',8,8,1,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'private',19,NULL,now()),
  ('70000000-0000-0000-0000-000000000014','Salmon Lentil Salad','Warm salmon over lentil salad.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',12,12,2,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'public',20,NULL,now()),
  ('70000000-0000-0000-0000-000000000015','Test Recipe 21','Auto-generated test recipe 21.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',11,26,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'public',21,NULL,now()),
  ('70000000-0000-0000-0000-000000000016','Test Recipe 22','Auto-generated test recipe 22.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',12,27,3,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'public',22,NULL,now()),
  ('70000000-0000-0000-0000-000000000017','Test Recipe 23','Auto-generated test recipe 23.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',13,28,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'public',23,NULL,now()),
  ('70000000-0000-0000-0000-000000000018','Test Recipe 24','Auto-generated test recipe 24.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',14,29,5,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'private',24,NULL,now()),
  ('70000000-0000-0000-0000-000000000019','Test Recipe 25','Auto-generated test recipe 25.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',15,30,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'public',25,NULL,now()),
  ('70000000-0000-0000-0000-00000000001a','Test Recipe 26','Auto-generated test recipe 26.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',16,31,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'public',26,NULL,now()),
  ('70000000-0000-0000-0000-00000000001b','Test Recipe 27','Auto-generated test recipe 27.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',17,32,3,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'public',27,NULL,now()),
  ('70000000-0000-0000-0000-00000000001c','Test Recipe 28','Auto-generated test recipe 28.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',18,33,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'private',28,NULL,now()),
  ('70000000-0000-0000-0000-00000000001d','Test Recipe 29','Auto-generated test recipe 29.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',19,34,5,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'public',29,NULL,now()),
  ('70000000-0000-0000-0000-00000000001e','Test Recipe 30','Auto-generated test recipe 30.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',5,35,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'public',30,NULL,now()),
  ('70000000-0000-0000-0000-00000000001f','Test Recipe 31','Auto-generated test recipe 31.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',6,36,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'public',31,NULL,now()),
  ('70000000-0000-0000-0000-000000000020','Test Recipe 32','Auto-generated test recipe 32.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',7,37,3,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'private',32,NULL,now()),
  ('70000000-0000-0000-0000-000000000021','Test Recipe 33','Auto-generated test recipe 33.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',8,38,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'public',33,NULL,now()),
  ('70000000-0000-0000-0000-000000000022','Test Recipe 34','Auto-generated test recipe 34.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',9,39,5,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'public',34,NULL,now()),
  ('70000000-0000-0000-0000-000000000023','Test Recipe 35','Auto-generated test recipe 35.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',10,40,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'public',35,NULL,now()),
  ('70000000-0000-0000-0000-000000000024','Test Recipe 36','Auto-generated test recipe 36.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',11,41,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'private',36,NULL,now()),
  ('70000000-0000-0000-0000-000000000025','Test Recipe 37','Auto-generated test recipe 37.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',12,42,3,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'public',37,NULL,now()),
  ('70000000-0000-0000-0000-000000000026','Test Recipe 38','Auto-generated test recipe 38.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',13,43,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'public',38,NULL,now()),
  ('70000000-0000-0000-0000-000000000027','Test Recipe 39','Auto-generated test recipe 39.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',14,44,5,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'public',39,NULL,now()),
  ('70000000-0000-0000-0000-000000000028','Test Recipe 40','Auto-generated test recipe 40.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',15,5,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'private',40,NULL,now()),
  ('70000000-0000-0000-0000-000000000029','Test Recipe 41','Auto-generated test recipe 41.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',16,6,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'public',41,NULL,now()),
  ('70000000-0000-0000-0000-00000000002a','Test Recipe 42','Auto-generated test recipe 42.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',17,7,3,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'public',42,NULL,now()),
  ('70000000-0000-0000-0000-00000000002b','Test Recipe 43','Auto-generated test recipe 43.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',18,8,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'public',43,NULL,now()),
  ('70000000-0000-0000-0000-00000000002c','Test Recipe 44','Auto-generated test recipe 44.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',19,9,5,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'private',44,NULL,now()),
  ('70000000-0000-0000-0000-00000000002d','Test Recipe 45','Auto-generated test recipe 45.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',5,10,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'public',45,NULL,now()),
  ('70000000-0000-0000-0000-00000000002e','Test Recipe 46','Auto-generated test recipe 46.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',6,11,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'public',46,NULL,now()),
  ('70000000-0000-0000-0000-00000000002f','Test Recipe 47','Auto-generated test recipe 47.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',7,12,3,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'public',47,NULL,now()),
  ('70000000-0000-0000-0000-000000000030','Test Recipe 48','Auto-generated test recipe 48.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',8,13,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'private',48,NULL,now()),
  ('70000000-0000-0000-0000-000000000031','Test Recipe 49','Auto-generated test recipe 49.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',9,14,5,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'public',49,NULL,now()),
  ('70000000-0000-0000-0000-000000000032','Test Recipe 50','Auto-generated test recipe 50.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',10,15,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'public',50,NULL,now()),
  ('70000000-0000-0000-0000-000000000033','Test Recipe 51','Auto-generated test recipe 51.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',11,16,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'public',51,NULL,now()),
  ('70000000-0000-0000-0000-000000000034','Test Recipe 52','Auto-generated test recipe 52.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',12,17,3,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'private',52,NULL,now()),
  ('70000000-0000-0000-0000-000000000035','Test Recipe 53','Auto-generated test recipe 53.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',13,18,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'public',53,NULL,now()),
  ('70000000-0000-0000-0000-000000000036','Test Recipe 54','Auto-generated test recipe 54.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',14,19,5,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'public',54,NULL,now()),
  ('70000000-0000-0000-0000-000000000037','Test Recipe 55','Auto-generated test recipe 55.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',15,20,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'public',55,NULL,now()),
  ('70000000-0000-0000-0000-000000000038','Test Recipe 56','Auto-generated test recipe 56.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',16,21,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'private',56,NULL,now()),
  ('70000000-0000-0000-0000-000000000039','Test Recipe 57','Auto-generated test recipe 57.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',17,22,3,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'public',57,NULL,now()),
  ('70000000-0000-0000-0000-00000000003a','Test Recipe 58','Auto-generated test recipe 58.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',18,23,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'public',58,NULL,now()),
  ('70000000-0000-0000-0000-00000000003b','Test Recipe 59','Auto-generated test recipe 59.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',19,24,5,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'public',59,NULL,now()),
  ('70000000-0000-0000-0000-00000000003c','Test Recipe 60','Auto-generated test recipe 60.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',5,25,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'private',60,NULL,now()),
  ('70000000-0000-0000-0000-00000000003d','Test Recipe 61','Auto-generated test recipe 61.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',6,26,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'public',61,NULL,now()),
  ('70000000-0000-0000-0000-00000000003e','Test Recipe 62','Auto-generated test recipe 62.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',7,27,3,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'public',62,NULL,now()),
  ('70000000-0000-0000-0000-00000000003f','Test Recipe 63','Auto-generated test recipe 63.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',8,28,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'public',63,NULL,now()),
  ('70000000-0000-0000-0000-000000000040','Test Recipe 64','Auto-generated test recipe 64.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',9,29,5,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'private',64,NULL,now()),
  ('70000000-0000-0000-0000-000000000041','Test Recipe 65','Auto-generated test recipe 65.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',10,30,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'public',65,NULL,now()),
  ('70000000-0000-0000-0000-000000000042','Test Recipe 66','Auto-generated test recipe 66.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',11,31,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'public',66,NULL,now()),
  ('70000000-0000-0000-0000-000000000043','Test Recipe 67','Auto-generated test recipe 67.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',12,32,3,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'public',67,NULL,now()),
  ('70000000-0000-0000-0000-000000000044','Test Recipe 68','Auto-generated test recipe 68.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',13,33,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'private',68,NULL,now()),
  ('70000000-0000-0000-0000-000000000045','Test Recipe 69','Auto-generated test recipe 69.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',14,34,5,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'public',69,NULL,now()),
  ('70000000-0000-0000-0000-000000000046','Test Recipe 70','Auto-generated test recipe 70.','https://picsum.photos/seed/chefai-rec-/1024/768','https://picsum.photos/seed/chefai-rec-/320/240',15,35,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'public',70,NULL,now());

-- ===============================
-- RECIPE_INGREDIENTS
-- ===============================
INSERT INTO recipe_ingredients (recipe_id, ingredient_id, quantity, unit, updated_at, deleted_at, server_updated_at) VALUES
  ('70000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000009',200,'g',1,NULL,now()),
  ('70000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-00000000000a',150,'g',1,NULL,now()),
  ('70000000-0000-0000-0000-000000000002','60000000-0000-0000-0000-000000000004',100,'g',2,NULL,now()),
  ('70000000-0000-0000-0000-000000000002','60000000-0000-0000-0000-000000000005',50,'g',2,NULL,now()),
  ('70000000-0000-0000-0000-000000000003','60000000-0000-0000-0000-000000000005',100,'g',3,NULL,now()),
  ('70000000-0000-0000-0000-000000000003','60000000-0000-0000-0000-000000000006',50,'g',3,NULL,now()),
  ('70000000-0000-0000-0000-000000000004','60000000-0000-0000-0000-000000000005',60,'g',4,NULL,now()),
  ('70000000-0000-0000-0000-000000000004','60000000-0000-0000-0000-000000000006',80,'g',4,NULL,now()),
  ('70000000-0000-0000-0000-000000000005','60000000-0000-0000-0000-000000000002',2,'pcs',5,NULL,now()),
  ('70000000-0000-0000-0000-000000000005','60000000-0000-0000-0000-000000000007',20,'g',5,NULL,now()),
  ('70000000-0000-0000-0000-000000000006','60000000-0000-0000-0000-000000000008',40,'g',6,NULL,now()),
  ('70000000-0000-0000-0000-000000000006','60000000-0000-0000-0000-00000000000b',5,'g',6,NULL,now()),
  ('70000000-0000-0000-0000-000000000007','60000000-0000-0000-0000-00000000000a',120,'g',7,NULL,now()),
  ('70000000-0000-0000-0000-000000000007','60000000-0000-0000-0000-000000000003',80,'ml',7,NULL,now()),
  ('70000000-0000-0000-0000-000000000008','60000000-0000-0000-0000-000000000008',50,'g',8,NULL,now()),
  ('70000000-0000-0000-0000-000000000008','60000000-0000-0000-0000-000000000001',70,'g',8,NULL,now()),
  ('70000000-0000-0000-0000-000000000009','60000000-0000-0000-0000-000000000006',90,'g',9,NULL,now()),
  ('70000000-0000-0000-0000-000000000009','60000000-0000-0000-0000-000000000007',60,'g',9,NULL,now()),
  ('70000000-0000-0000-0000-00000000000a','60000000-0000-0000-0000-000000000002',2,'pcs',10,NULL,now()),
  ('70000000-0000-0000-0000-00000000000a','60000000-0000-0000-0000-00000000000a',150,'g',10,NULL,now()),
  ('70000000-0000-0000-0000-00000000000b','60000000-0000-0000-0000-000000000004',180,'g',11,NULL,now()),
  ('70000000-0000-0000-0000-00000000000b','60000000-0000-0000-0000-00000000000b',8,'g',11,NULL,now()),
  ('70000000-0000-0000-0000-00000000000c','60000000-0000-0000-0000-000000000010',160,'g',12,NULL,now()),
  ('70000000-0000-0000-0000-00000000000c','60000000-0000-0000-0000-00000000000e',90,'g',12,NULL,now()),
  ('70000000-0000-0000-0000-00000000000d','60000000-0000-0000-0000-00000000000f',170,'g',13,NULL,now()),
  ('70000000-0000-0000-0000-00000000000d','60000000-0000-0000-0000-00000000000d',70,'g',13,NULL,now()),
  ('70000000-0000-0000-0000-00000000000e','60000000-0000-0000-0000-000000000011',140,'g',14,NULL,now()),
  ('70000000-0000-0000-0000-00000000000e','60000000-0000-0000-0000-000000000005',90,'g',14,NULL,now()),
  ('70000000-0000-0000-0000-00000000000f','60000000-0000-0000-0000-000000000013',70,'g',15,NULL,now()),
  ('70000000-0000-0000-0000-00000000000f','60000000-0000-0000-0000-000000000006',40,'g',15,NULL,now()),
  ('70000000-0000-0000-0000-000000000010','60000000-0000-0000-0000-000000000014',180,'g',16,NULL,now()),
  ('70000000-0000-0000-0000-000000000010','60000000-0000-0000-0000-000000000005',100,'g',16,NULL,now()),
  ('70000000-0000-0000-0000-000000000011','60000000-0000-0000-0000-000000000012',180,'g',17,NULL,now()),
  ('70000000-0000-0000-0000-000000000011','60000000-0000-0000-0000-000000000008',20,'g',17,NULL,now()),
  ('70000000-0000-0000-0000-000000000012','60000000-0000-0000-0000-00000000000a',180,'g',18,NULL,now()),
  ('70000000-0000-0000-0000-000000000012','60000000-0000-0000-0000-00000000000e',110,'g',18,NULL,now()),
  ('70000000-0000-0000-0000-000000000013','60000000-0000-0000-0000-000000000002',3,'pcs',19,NULL,now()),
  ('70000000-0000-0000-0000-000000000013','60000000-0000-0000-0000-00000000000c',45,'g',19,NULL,now()),
  ('70000000-0000-0000-0000-000000000014','60000000-0000-0000-0000-000000000009',160,'g',20,NULL,now()),
  ('70000000-0000-0000-0000-000000000014','60000000-0000-0000-0000-000000000014',120,'g',20,NULL,now());

-- ===============================
-- RECIPE_LABELS
-- ===============================
INSERT INTO recipe_labels (recipe_id, label_id, updated_at, deleted_at, server_updated_at) VALUES
  ('70000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000009',1,NULL,now()),
  ('70000000-0000-0000-0000-000000000002','40000000-0000-0000-0000-000000000006',2,NULL,now()),
  ('70000000-0000-0000-0000-000000000003','40000000-0000-0000-0000-000000000002',3,NULL,now()),
  ('70000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000001',4,NULL,now()),
  ('70000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000004',5,NULL,now()),
  ('70000000-0000-0000-0000-000000000006','40000000-0000-0000-0000-000000000005',6,NULL,now()),
  ('70000000-0000-0000-0000-000000000007','40000000-0000-0000-0000-000000000003',7,NULL,now()),
  ('70000000-0000-0000-0000-000000000008','40000000-0000-0000-0000-000000000005',8,NULL,now()),
  ('70000000-0000-0000-0000-000000000009','40000000-0000-0000-0000-000000000002',9,NULL,now()),
  ('70000000-0000-0000-0000-00000000000a','40000000-0000-0000-0000-000000000007',10,NULL,now()),
  ('70000000-0000-0000-0000-00000000000b','40000000-0000-0000-0000-000000000006',11,NULL,now()),
  ('70000000-0000-0000-0000-00000000000c','40000000-0000-0000-0000-000000000001',12,NULL,now()),
  ('70000000-0000-0000-0000-00000000000d','40000000-0000-0000-0000-000000000006',13,NULL,now()),
  ('70000000-0000-0000-0000-00000000000e','40000000-0000-0000-0000-00000000000a',14,NULL,now()),
  ('70000000-0000-0000-0000-00000000000f','40000000-0000-0000-0000-000000000002',15,NULL,now()),
  ('70000000-0000-0000-0000-000000000010','40000000-0000-0000-0000-000000000001',16,NULL,now()),
  ('70000000-0000-0000-0000-000000000011','40000000-0000-0000-0000-000000000002',17,NULL,now()),
  ('70000000-0000-0000-0000-000000000012','40000000-0000-0000-0000-000000000004',18,NULL,now()),
  ('70000000-0000-0000-0000-000000000013','40000000-0000-0000-0000-000000000007',19,NULL,now()),
  ('70000000-0000-0000-0000-000000000014','40000000-0000-0000-0000-000000000006',20,NULL,now());

-- ===============================
-- RECIPE_TAGS
-- ===============================
INSERT INTO recipe_tags (recipe_id, tag_id, updated_at, deleted_at, server_updated_at) VALUES
  ('70000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000005',1,NULL,now()),
  ('70000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000004',2,NULL,now()),
  ('70000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000001',3,NULL,now()),
  ('70000000-0000-0000-0000-000000000004','50000000-0000-0000-0000-000000000003',4,NULL,now()),
  ('70000000-0000-0000-0000-000000000005','50000000-0000-0000-0000-000000000006',5,NULL,now()),
  ('70000000-0000-0000-0000-000000000006','50000000-0000-0000-0000-000000000007',6,NULL,now()),
  ('70000000-0000-0000-0000-000000000007','50000000-0000-0000-0000-000000000008',7,NULL,now()),
  ('70000000-0000-0000-0000-000000000008','50000000-0000-0000-0000-000000000002',8,NULL,now()),
  ('70000000-0000-0000-0000-000000000009','50000000-0000-0000-0000-000000000009',9,NULL,now()),
  ('70000000-0000-0000-0000-00000000000a','50000000-0000-0000-0000-00000000000a',10,NULL,now()),
  ('70000000-0000-0000-0000-00000000000b','50000000-0000-0000-0000-000000000006',11,NULL,now()),
  ('70000000-0000-0000-0000-00000000000c','50000000-0000-0000-0000-000000000005',12,NULL,now()),
  ('70000000-0000-0000-0000-00000000000d','50000000-0000-0000-0000-000000000003',13,NULL,now()),
  ('70000000-0000-0000-0000-00000000000e','50000000-0000-0000-0000-000000000001',14,NULL,now()),
  ('70000000-0000-0000-0000-00000000000f','50000000-0000-0000-0000-000000000004',15,NULL,now()),
  ('70000000-0000-0000-0000-000000000010','50000000-0000-0000-0000-000000000006',16,NULL,now()),
  ('70000000-0000-0000-0000-000000000011','50000000-0000-0000-0000-000000000007',17,NULL,now()),
  ('70000000-0000-0000-0000-000000000012','50000000-0000-0000-0000-000000000005',18,NULL,now()),
  ('70000000-0000-0000-0000-000000000013','50000000-0000-0000-0000-000000000004',19,NULL,now()),
  ('70000000-0000-0000-0000-000000000014','50000000-0000-0000-0000-000000000005',20,NULL,now());

-- ===============================
-- RECIPE_STEPS
-- ===============================
INSERT INTO recipe_steps (uuid, recipe_id, order_index, instruction, updated_at, deleted_at, server_updated_at) VALUES
  ('80000000-0000-0000-0000-000000000001','70000000-0000-0000-0000-000000000001',1,'Prepare salmon',1,NULL,now()),
  ('80000000-0000-0000-0000-000000000002','70000000-0000-0000-0000-000000000001',2,'Cook rice',1,NULL,now()),
  ('80000000-0000-0000-0000-000000000003','70000000-0000-0000-0000-000000000002',1,'Chop chicken',2,NULL,now()),
  ('80000000-0000-0000-0000-000000000004','70000000-0000-0000-0000-000000000003',1,'Boil tomatoes',3,NULL,now()),
  ('80000000-0000-0000-0000-000000000005','70000000-0000-0000-0000-000000000004',1,'Stir fry all veggies',4,NULL,now()),
  ('80000000-0000-0000-0000-000000000006','70000000-0000-0000-0000-000000000005',1,'Whisk eggs and cheese',5,NULL,now()),
  ('80000000-0000-0000-0000-000000000007','70000000-0000-0000-0000-000000000006',1,'Mix nuts with spices',6,NULL,now()),
  ('80000000-0000-0000-0000-000000000008','70000000-0000-0000-0000-000000000007',1,'Cook rice with milk',7,NULL,now()),
  ('80000000-0000-0000-0000-000000000009','70000000-0000-0000-0000-000000000008',1,'Prepare almond dough',8,NULL,now()),
  ('80000000-0000-0000-0000-00000000000a','70000000-0000-0000-0000-000000000009',1,'Layer spinach and cheese',9,NULL,now()),
  ('80000000-0000-0000-0000-00000000000b','70000000-0000-0000-0000-00000000000a',1,'Stir-fry eggs with rice',10,NULL,now()),
  ('80000000-0000-0000-0000-00000000000c','70000000-0000-0000-0000-00000000000b',1,'Sear chicken with garlic',11,NULL,now()),
  ('80000000-0000-0000-0000-00000000000d','70000000-0000-0000-0000-00000000000c',1,'Pan-fry tofu and broccoli',12,NULL,now()),
  ('80000000-0000-0000-0000-00000000000e','70000000-0000-0000-0000-00000000000d',1,'Stir-fry beef with peppers',13,NULL,now()),
  ('80000000-0000-0000-0000-00000000000f','70000000-0000-0000-0000-00000000000e',1,'Cook shrimp with tomato sauce',14,NULL,now()),
  ('80000000-0000-0000-0000-000000000010','70000000-0000-0000-0000-00000000000f',1,'Cook oats and fold in spinach',15,NULL,now()),
  ('80000000-0000-0000-0000-000000000011','70000000-0000-0000-0000-000000000010',1,'Simmer lentils with tomato',16,NULL,now()),
  ('80000000-0000-0000-0000-000000000012','70000000-0000-0000-0000-000000000011',1,'Assemble chilled yogurt bowl',17,NULL,now()),
  ('80000000-0000-0000-0000-000000000013','70000000-0000-0000-0000-000000000012',1,'Bake rice and broccoli together',18,NULL,now()),
  ('80000000-0000-0000-0000-000000000014','70000000-0000-0000-0000-000000000013',1,'Cook omelette and wrap filling',19,NULL,now()),
  ('80000000-0000-0000-0000-000000000015','70000000-0000-0000-0000-000000000014',1,'Top lentils with pan-seared salmon',20,NULL,now());

-- ===============================
-- REFRESH TOKENS
-- ===============================
INSERT INTO refresh_tokens (uuid, user_id, token_hash, is_revoked, revoked_at, expires_at, created_at) VALUES
  ('90000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'tokhash1', FALSE, NULL, NOW() + INTERVAL '30 days', now()),
  ('90000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'tokhash2', FALSE, NULL, NOW() + INTERVAL '30 days', now()),
  ('90000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 'tokhash3', FALSE, NULL, NOW() + INTERVAL '30 days', now());

-- ===============================
-- HOME LAYOUT FEATURED RECIPES
-- These UUIDs are hardcoded in home_layout.json (SDUI). They must be PUBLIC
-- so any authenticated user can bookmark them.
-- ===============================
INSERT INTO recipes (uuid, title, description, image_url, image_url_thumbnail, prep_time_minutes, cook_time_minutes, servings, creator_id, recipe_external_url, privacy, updated_at, deleted_at, server_updated_at) VALUES
  ('a1b2c3d4-0000-0000-0000-000000000001','Paella','Classic Spanish seafood and rice dish.','https://picsum.photos/seed/chefai-home-1/1024/768','https://picsum.photos/seed/chefai-home-1/320/240',20,40,4,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',1,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000002','Grilled Chicken','Juicy grilled chicken breast with herbs.','https://picsum.photos/seed/chefai-home-2/1024/768','https://picsum.photos/seed/chefai-home-2/320/240',10,20,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',2,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000003','Thai Green Curry','Aromatic Thai curry with coconut milk.','https://picsum.photos/seed/chefai-home-3/1024/768','https://picsum.photos/seed/chefai-home-3/320/240',15,25,3,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',3,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000004','Margherita Pizza','Traditional Neapolitan pizza.','https://picsum.photos/seed/chefai-home-4/1024/768','https://picsum.photos/seed/chefai-home-4/320/240',20,15,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',4,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000005','Beef Stew','Hearty slow-cooked beef and vegetable stew.','https://picsum.photos/seed/chefai-home-5/1024/768','https://picsum.photos/seed/chefai-home-5/320/240',20,90,6,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',5,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000006','Carbonara','Creamy Italian pasta with egg and guanciale.','https://picsum.photos/seed/chefai-home-6/1024/768','https://picsum.photos/seed/chefai-home-6/320/240',10,15,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',6,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000007','Lasagna','Layered pasta with meat sauce and béchamel.','https://picsum.photos/seed/chefai-home-7/1024/768','https://picsum.photos/seed/chefai-home-7/320/240',30,60,6,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',7,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000008','Tuscan Sausage Pasta','Rich pasta with sausage and sun-dried tomato.','https://picsum.photos/seed/chefai-home-8/1024/768','https://picsum.photos/seed/chefai-home-8/320/240',10,20,3,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',8,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000009','Salmon Teriyaki','Glazed salmon with teriyaki sauce.','https://picsum.photos/seed/chefai-home-9/1024/768','https://picsum.photos/seed/chefai-home-9/320/240',10,15,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',9,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000010','Sushi Nigiris','Hand-pressed sushi with fresh fish.','https://picsum.photos/seed/chefai-home-10/1024/768','https://picsum.photos/seed/chefai-home-10/320/240',30,0,4,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',10,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000011','Shrimp Ceviche','Fresh shrimp cured in citrus juice.','https://picsum.photos/seed/chefai-home-11/1024/768','https://picsum.photos/seed/chefai-home-11/320/240',20,0,3,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',11,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000012','Mediterranean Chicken','Grilled chicken with olives and feta.','https://picsum.photos/seed/chefai-home-12/1024/768','https://picsum.photos/seed/chefai-home-12/320/240',15,25,3,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',12,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000013','Battered Cod','Crispy beer-battered cod fillets.','https://picsum.photos/seed/chefai-home-13/1024/768','https://picsum.photos/seed/chefai-home-13/320/240',15,10,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',13,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000014','Chocolate Chip Cookies','Classic soft-baked cookies.','https://picsum.photos/seed/chefai-home-14/1024/768','https://picsum.photos/seed/chefai-home-14/320/240',15,12,24,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',14,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000015','Blueberry Cheesecake','Creamy cheesecake with blueberry topping.','https://picsum.photos/seed/chefai-home-15/1024/768','https://picsum.photos/seed/chefai-home-15/320/240',30,60,8,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',15,NULL,now()),
  ('a1b2c3d4-0000-0000-0000-000000000016','Ghirardelli Cookies','Rich double-chocolate cookies.','https://picsum.photos/seed/chefai-home-16/1024/768','https://picsum.photos/seed/chefai-home-16/320/240',20,11,18,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',16,NULL,now());

-- ===============================
-- 20 COMPLETE PUBLIC RECIPES (71-90)
-- Each recipe has ingredients, steps, tags and labels.
-- Creators rotate across the 5 seed users.
-- ===============================
INSERT INTO recipes (uuid, title, description, image_url, image_url_thumbnail, prep_time_minutes, cook_time_minutes, servings, creator_id, recipe_external_url, privacy, updated_at, deleted_at, server_updated_at) VALUES
  ('70000000-0000-0000-0000-000000000047','Chicken Tikka Masala','Tender chicken in a rich spiced tomato cream sauce.','https://picsum.photos/seed/chefai-rec-47/1024/768','https://picsum.photos/seed/chefai-rec-47/320/240',20,30,4,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',71,NULL,now()),
  ('70000000-0000-0000-0000-000000000048','Beef Tacos','Seasoned ground beef in crispy taco shells with fresh toppings.','https://picsum.photos/seed/chefai-rec-48/1024/768','https://picsum.photos/seed/chefai-rec-48/320/240',15,15,4,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'PUBLIC',72,NULL,now()),
  ('70000000-0000-0000-0000-000000000049','Greek Salad','Classic salad with tomato, feta and leafy greens.','https://picsum.photos/seed/chefai-rec-49/1024/768','https://picsum.photos/seed/chefai-rec-49/320/240',10,0,2,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'PUBLIC',73,NULL,now()),
  ('70000000-0000-0000-0000-00000000004a','Tomato Cheese Risotto','Creamy Arborio-style risotto with fresh tomato and Parmesan.','https://picsum.photos/seed/chefai-rec-4a/1024/768','https://picsum.photos/seed/chefai-rec-4a/320/240',10,30,3,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'PUBLIC',74,NULL,now()),
  ('70000000-0000-0000-0000-00000000004b','French Onion Soup','Deeply caramelised onion soup topped with melted cheese.','https://picsum.photos/seed/chefai-rec-4b/1024/768','https://picsum.photos/seed/chefai-rec-4b/320/240',15,45,4,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'PUBLIC',75,NULL,now()),
  ('70000000-0000-0000-0000-00000000004c','Chicken Caesar Salad','Grilled chicken over crisp greens with Caesar dressing.','https://picsum.photos/seed/chefai-rec-4c/1024/768','https://picsum.photos/seed/chefai-rec-4c/320/240',10,15,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',76,NULL,now()),
  ('70000000-0000-0000-0000-00000000004d','Spicy Vegetable Curry','Aromatic vegan curry with broccoli, spinach and tomato.','https://picsum.photos/seed/chefai-rec-4d/1024/768','https://picsum.photos/seed/chefai-rec-4d/320/240',10,25,3,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'PUBLIC',77,NULL,now()),
  ('70000000-0000-0000-0000-00000000004e','Salmon with Broccoli','Oven-baked salmon fillet with garlic-butter broccoli.','https://picsum.photos/seed/chefai-rec-4e/1024/768','https://picsum.photos/seed/chefai-rec-4e/320/240',10,15,2,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'PUBLIC',78,NULL,now()),
  ('70000000-0000-0000-0000-00000000004f','Beef Burger Bowl','Juicy beef burger served bowl-style over rice with toppings.','https://picsum.photos/seed/chefai-rec-4f/1024/768','https://picsum.photos/seed/chefai-rec-4f/320/240',10,20,2,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'PUBLIC',79,NULL,now()),
  ('70000000-0000-0000-0000-000000000050','Egg & Spinach Toast','Scrambled eggs with wilted spinach on toasted bread.','https://picsum.photos/seed/chefai-rec-50/1024/768','https://picsum.photos/seed/chefai-rec-50/320/240',5,10,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'PUBLIC',80,NULL,now()),
  ('70000000-0000-0000-0000-000000000051','Chocolate Mousse','Light and airy chocolate mousse with almond shavings.','https://picsum.photos/seed/chefai-rec-51/1024/768','https://picsum.photos/seed/chefai-rec-51/320/240',20,0,4,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',81,NULL,now()),
  ('70000000-0000-0000-0000-000000000052','Fluffy Pancakes','Classic stack of golden buttermilk-style pancakes.','https://picsum.photos/seed/chefai-rec-52/1024/768','https://picsum.photos/seed/chefai-rec-52/320/240',10,15,2,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'PUBLIC',82,NULL,now()),
  ('70000000-0000-0000-0000-000000000053','Lentil Soup','Hearty vegan lentil soup with tomato and onion.','https://picsum.photos/seed/chefai-rec-53/1024/768','https://picsum.photos/seed/chefai-rec-53/320/240',10,30,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'PUBLIC',83,NULL,now()),
  ('70000000-0000-0000-0000-000000000054','Rice & Veggie Bowl','Fluffy rice topped with steamed broccoli and bell pepper.','https://picsum.photos/seed/chefai-rec-54/1024/768','https://picsum.photos/seed/chefai-rec-54/320/240',5,15,2,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'PUBLIC',84,NULL,now()),
  ('70000000-0000-0000-0000-000000000055','Chicken Wrap','Quick chicken, spinach and tomato wrap.','https://picsum.photos/seed/chefai-rec-55/1024/768','https://picsum.photos/seed/chefai-rec-55/320/240',5,10,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'PUBLIC',85,NULL,now()),
  ('70000000-0000-0000-0000-000000000056','Shakshuka','Eggs poached in a spiced tomato and pepper sauce.','https://picsum.photos/seed/chefai-rec-56/1024/768','https://picsum.photos/seed/chefai-rec-56/320/240',10,20,2,'1ff5c5d2-dda5-4a7d-9df2-0b1d79878fd7',NULL,'PUBLIC',86,NULL,now()),
  ('70000000-0000-0000-0000-000000000057','Yogurt Almond Bowl','Creamy yogurt with toasted oats and almonds.','https://picsum.photos/seed/chefai-rec-57/1024/768','https://picsum.photos/seed/chefai-rec-57/320/240',5,0,1,'29002be5-01a8-49d8-b2bd-25ad73745d13',NULL,'PUBLIC',87,NULL,now()),
  ('70000000-0000-0000-0000-000000000058','Stuffed Bell Peppers','Bell peppers filled with seasoned beef and rice, oven-baked.','https://picsum.photos/seed/chefai-rec-58/1024/768','https://picsum.photos/seed/chefai-rec-58/320/240',15,30,4,'d3c3a63a-0705-472a-a97f-d5edfde8aaa1',NULL,'PUBLIC',88,NULL,now()),
  ('70000000-0000-0000-0000-000000000059','Garlic Shrimp Stir-Fry','Quick shrimp and broccoli stir-fry with garlic soy glaze.','https://picsum.photos/seed/chefai-rec-59/1024/768','https://picsum.photos/seed/chefai-rec-59/320/240',5,10,2,'d7773142-d89f-495a-9dda-aa4cd0a2ea84',NULL,'PUBLIC',89,NULL,now()),
  ('70000000-0000-0000-0000-00000000005a','Overnight Oats','No-cook oats soaked overnight in milk with yogurt.','https://picsum.photos/seed/chefai-rec-5a/1024/768','https://picsum.photos/seed/chefai-rec-5a/320/240',5,0,1,'e69845ff-5a3d-49f8-9ed4-f0f956ec8779',NULL,'PUBLIC',90,NULL,now());

INSERT INTO recipe_ingredients (recipe_id, ingredient_id, quantity, unit, updated_at, deleted_at, server_updated_at) VALUES
  -- 47 Chicken Tikka Masala
  ('70000000-0000-0000-0000-000000000047','60000000-0000-0000-0000-000000000004',300,'g',71,NULL,now()),
  ('70000000-0000-0000-0000-000000000047','60000000-0000-0000-0000-000000000005',200,'g',71,NULL,now()),
  ('70000000-0000-0000-0000-000000000047','60000000-0000-0000-0000-00000000000b',15,'g',71,NULL,now()),
  -- 48 Beef Tacos
  ('70000000-0000-0000-0000-000000000048','60000000-0000-0000-0000-00000000000f',250,'g',72,NULL,now()),
  ('70000000-0000-0000-0000-000000000048','60000000-0000-0000-0000-00000000000c',100,'g',72,NULL,now()),
  ('70000000-0000-0000-0000-000000000048','60000000-0000-0000-0000-00000000000d',80,'g',72,NULL,now()),
  -- 49 Greek Salad
  ('70000000-0000-0000-0000-000000000049','60000000-0000-0000-0000-000000000005',200,'g',73,NULL,now()),
  ('70000000-0000-0000-0000-000000000049','60000000-0000-0000-0000-000000000007',80,'g',73,NULL,now()),
  ('70000000-0000-0000-0000-000000000049','60000000-0000-0000-0000-000000000006',100,'g',73,NULL,now()),
  -- 4a Tomato Cheese Risotto
  ('70000000-0000-0000-0000-00000000004a','60000000-0000-0000-0000-00000000000a',200,'g',74,NULL,now()),
  ('70000000-0000-0000-0000-00000000004a','60000000-0000-0000-0000-000000000005',150,'g',74,NULL,now()),
  ('70000000-0000-0000-0000-00000000004a','60000000-0000-0000-0000-000000000007',60,'g',74,NULL,now()),
  -- 4b French Onion Soup
  ('70000000-0000-0000-0000-00000000004b','60000000-0000-0000-0000-00000000000c',400,'g',75,NULL,now()),
  ('70000000-0000-0000-0000-00000000004b','60000000-0000-0000-0000-000000000007',100,'g',75,NULL,now()),
  -- 4c Chicken Caesar Salad
  ('70000000-0000-0000-0000-00000000004c','60000000-0000-0000-0000-000000000004',200,'g',76,NULL,now()),
  ('70000000-0000-0000-0000-00000000004c','60000000-0000-0000-0000-000000000006',150,'g',76,NULL,now()),
  ('70000000-0000-0000-0000-00000000004c','60000000-0000-0000-0000-000000000007',40,'g',76,NULL,now()),
  -- 4d Spicy Vegetable Curry
  ('70000000-0000-0000-0000-00000000004d','60000000-0000-0000-0000-000000000006',100,'g',77,NULL,now()),
  ('70000000-0000-0000-0000-00000000004d','60000000-0000-0000-0000-000000000005',200,'g',77,NULL,now()),
  ('70000000-0000-0000-0000-00000000004d','60000000-0000-0000-0000-00000000000e',150,'g',77,NULL,now()),
  -- 4e Salmon with Broccoli
  ('70000000-0000-0000-0000-00000000004e','60000000-0000-0000-0000-000000000009',200,'g',78,NULL,now()),
  ('70000000-0000-0000-0000-00000000004e','60000000-0000-0000-0000-00000000000e',200,'g',78,NULL,now()),
  ('70000000-0000-0000-0000-00000000004e','60000000-0000-0000-0000-00000000000b',10,'g',78,NULL,now()),
  -- 4f Beef Burger Bowl
  ('70000000-0000-0000-0000-00000000004f','60000000-0000-0000-0000-00000000000f',250,'g',79,NULL,now()),
  ('70000000-0000-0000-0000-00000000004f','60000000-0000-0000-0000-00000000000c',80,'g',79,NULL,now()),
  ('70000000-0000-0000-0000-00000000004f','60000000-0000-0000-0000-000000000005',120,'g',79,NULL,now()),
  -- 50 Egg & Spinach Toast
  ('70000000-0000-0000-0000-000000000050','60000000-0000-0000-0000-000000000002',3,'pcs',80,NULL,now()),
  ('70000000-0000-0000-0000-000000000050','60000000-0000-0000-0000-000000000006',80,'g',80,NULL,now()),
  ('70000000-0000-0000-0000-000000000050','60000000-0000-0000-0000-000000000007',30,'g',80,NULL,now()),
  -- 51 Chocolate Mousse
  ('70000000-0000-0000-0000-000000000051','60000000-0000-0000-0000-000000000002',4,'pcs',81,NULL,now()),
  ('70000000-0000-0000-0000-000000000051','60000000-0000-0000-0000-000000000003',100,'ml',81,NULL,now()),
  ('70000000-0000-0000-0000-000000000051','60000000-0000-0000-0000-000000000008',20,'g',81,NULL,now()),
  -- 52 Fluffy Pancakes
  ('70000000-0000-0000-0000-000000000052','60000000-0000-0000-0000-000000000001',150,'g',82,NULL,now()),
  ('70000000-0000-0000-0000-000000000052','60000000-0000-0000-0000-000000000002',2,'pcs',82,NULL,now()),
  ('70000000-0000-0000-0000-000000000052','60000000-0000-0000-0000-000000000003',200,'ml',82,NULL,now()),
  -- 53 Lentil Soup
  ('70000000-0000-0000-0000-000000000053','60000000-0000-0000-0000-000000000014',200,'g',83,NULL,now()),
  ('70000000-0000-0000-0000-000000000053','60000000-0000-0000-0000-000000000005',150,'g',83,NULL,now()),
  ('70000000-0000-0000-0000-000000000053','60000000-0000-0000-0000-00000000000c',100,'g',83,NULL,now()),
  -- 54 Rice & Veggie Bowl
  ('70000000-0000-0000-0000-000000000054','60000000-0000-0000-0000-00000000000a',200,'g',84,NULL,now()),
  ('70000000-0000-0000-0000-000000000054','60000000-0000-0000-0000-00000000000e',150,'g',84,NULL,now()),
  ('70000000-0000-0000-0000-000000000054','60000000-0000-0000-0000-00000000000d',100,'g',84,NULL,now()),
  -- 55 Chicken Wrap
  ('70000000-0000-0000-0000-000000000055','60000000-0000-0000-0000-000000000004',150,'g',85,NULL,now()),
  ('70000000-0000-0000-0000-000000000055','60000000-0000-0000-0000-000000000006',80,'g',85,NULL,now()),
  ('70000000-0000-0000-0000-000000000055','60000000-0000-0000-0000-000000000005',100,'g',85,NULL,now()),
  -- 56 Shakshuka
  ('70000000-0000-0000-0000-000000000056','60000000-0000-0000-0000-000000000002',4,'pcs',86,NULL,now()),
  ('70000000-0000-0000-0000-000000000056','60000000-0000-0000-0000-000000000005',300,'g',86,NULL,now()),
  ('70000000-0000-0000-0000-000000000056','60000000-0000-0000-0000-00000000000d',150,'g',86,NULL,now()),
  -- 57 Yogurt Almond Bowl
  ('70000000-0000-0000-0000-000000000057','60000000-0000-0000-0000-000000000012',200,'g',87,NULL,now()),
  ('70000000-0000-0000-0000-000000000057','60000000-0000-0000-0000-000000000008',30,'g',87,NULL,now()),
  ('70000000-0000-0000-0000-000000000057','60000000-0000-0000-0000-000000000013',50,'g',87,NULL,now()),
  -- 58 Stuffed Bell Peppers
  ('70000000-0000-0000-0000-000000000058','60000000-0000-0000-0000-00000000000d',400,'g',88,NULL,now()),
  ('70000000-0000-0000-0000-000000000058','60000000-0000-0000-0000-00000000000f',200,'g',88,NULL,now()),
  ('70000000-0000-0000-0000-000000000058','60000000-0000-0000-0000-00000000000a',100,'g',88,NULL,now()),
  -- 59 Garlic Shrimp Stir-Fry
  ('70000000-0000-0000-0000-000000000059','60000000-0000-0000-0000-000000000011',200,'g',89,NULL,now()),
  ('70000000-0000-0000-0000-000000000059','60000000-0000-0000-0000-00000000000b',15,'g',89,NULL,now()),
  ('70000000-0000-0000-0000-000000000059','60000000-0000-0000-0000-00000000000e',150,'g',89,NULL,now()),
  -- 5a Overnight Oats
  ('70000000-0000-0000-0000-00000000005a','60000000-0000-0000-0000-000000000013',80,'g',90,NULL,now()),
  ('70000000-0000-0000-0000-00000000005a','60000000-0000-0000-0000-000000000003',200,'ml',90,NULL,now()),
  ('70000000-0000-0000-0000-00000000005a','60000000-0000-0000-0000-000000000012',100,'g',90,NULL,now());

INSERT INTO recipe_labels (recipe_id, label_id, updated_at, deleted_at, server_updated_at) VALUES
  ('70000000-0000-0000-0000-000000000047','40000000-0000-0000-0000-000000000006',71,NULL,now()), -- High Protein
  ('70000000-0000-0000-0000-000000000048','40000000-0000-0000-0000-000000000006',72,NULL,now()), -- High Protein
  ('70000000-0000-0000-0000-000000000049','40000000-0000-0000-0000-000000000002',73,NULL,now()), -- Vegetarian
  ('70000000-0000-0000-0000-00000000004a','40000000-0000-0000-0000-000000000002',74,NULL,now()), -- Vegetarian
  ('70000000-0000-0000-0000-00000000004b','40000000-0000-0000-0000-000000000005',75,NULL,now()), -- Nut-Free
  ('70000000-0000-0000-0000-00000000004c','40000000-0000-0000-0000-000000000006',76,NULL,now()), -- High Protein
  ('70000000-0000-0000-0000-00000000004d','40000000-0000-0000-0000-000000000001',77,NULL,now()), -- Vegan
  ('70000000-0000-0000-0000-00000000004e','40000000-0000-0000-0000-000000000009',78,NULL,now()), -- Keto
  ('70000000-0000-0000-0000-00000000004f','40000000-0000-0000-0000-000000000006',79,NULL,now()), -- High Protein
  ('70000000-0000-0000-0000-000000000050','40000000-0000-0000-0000-000000000002',80,NULL,now()), -- Vegetarian
  ('70000000-0000-0000-0000-000000000051','40000000-0000-0000-0000-000000000003',81,NULL,now()), -- Gluten-Free
  ('70000000-0000-0000-0000-000000000052','40000000-0000-0000-0000-000000000002',82,NULL,now()), -- Vegetarian
  ('70000000-0000-0000-0000-000000000053','40000000-0000-0000-0000-000000000001',83,NULL,now()), -- Vegan
  ('70000000-0000-0000-0000-000000000054','40000000-0000-0000-0000-000000000001',84,NULL,now()), -- Vegan
  ('70000000-0000-0000-0000-000000000055','40000000-0000-0000-0000-000000000006',85,NULL,now()), -- High Protein
  ('70000000-0000-0000-0000-000000000056','40000000-0000-0000-0000-000000000002',86,NULL,now()), -- Vegetarian
  ('70000000-0000-0000-0000-000000000057','40000000-0000-0000-0000-000000000002',87,NULL,now()), -- Vegetarian
  ('70000000-0000-0000-0000-000000000058','40000000-0000-0000-0000-000000000007',88,NULL,now()), -- Low Carb
  ('70000000-0000-0000-0000-000000000059','40000000-0000-0000-0000-00000000000a',89,NULL,now()), -- Contains Seafood
  ('70000000-0000-0000-0000-00000000005a','40000000-0000-0000-0000-000000000002',90,NULL,now()); -- Vegetarian

INSERT INTO recipe_tags (recipe_id, tag_id, updated_at, deleted_at, server_updated_at) VALUES
  ('70000000-0000-0000-0000-000000000047','50000000-0000-0000-0000-000000000006',71,NULL,now()), -- Dinner
  ('70000000-0000-0000-0000-000000000048','50000000-0000-0000-0000-000000000006',72,NULL,now()), -- Dinner
  ('70000000-0000-0000-0000-000000000049','50000000-0000-0000-0000-000000000005',73,NULL,now()), -- Lunch
  ('70000000-0000-0000-0000-00000000004a','50000000-0000-0000-0000-000000000006',74,NULL,now()), -- Dinner
  ('70000000-0000-0000-0000-00000000004b','50000000-0000-0000-0000-000000000006',75,NULL,now()), -- Dinner
  ('70000000-0000-0000-0000-00000000004c','50000000-0000-0000-0000-000000000005',76,NULL,now()), -- Lunch
  ('70000000-0000-0000-0000-00000000004d','50000000-0000-0000-0000-000000000001',77,NULL,now()), -- Spicy
  ('70000000-0000-0000-0000-00000000004e','50000000-0000-0000-0000-000000000006',78,NULL,now()), -- Dinner
  ('70000000-0000-0000-0000-00000000004f','50000000-0000-0000-0000-000000000006',79,NULL,now()), -- Dinner
  ('70000000-0000-0000-0000-000000000050','50000000-0000-0000-0000-000000000004',80,NULL,now()), -- Breakfast
  ('70000000-0000-0000-0000-000000000051','50000000-0000-0000-0000-000000000008',81,NULL,now()), -- Dessert
  ('70000000-0000-0000-0000-000000000052','50000000-0000-0000-0000-000000000004',82,NULL,now()), -- Breakfast
  ('70000000-0000-0000-0000-000000000053','50000000-0000-0000-0000-000000000005',83,NULL,now()), -- Lunch
  ('70000000-0000-0000-0000-000000000054','50000000-0000-0000-0000-000000000005',84,NULL,now()), -- Lunch
  ('70000000-0000-0000-0000-000000000055','50000000-0000-0000-0000-00000000000a',85,NULL,now()), -- Quick
  ('70000000-0000-0000-0000-000000000056','50000000-0000-0000-0000-000000000001',86,NULL,now()), -- Spicy
  ('70000000-0000-0000-0000-000000000057','50000000-0000-0000-0000-000000000004',87,NULL,now()), -- Breakfast
  ('70000000-0000-0000-0000-000000000058','50000000-0000-0000-0000-000000000006',88,NULL,now()), -- Dinner
  ('70000000-0000-0000-0000-000000000059','50000000-0000-0000-0000-00000000000a',89,NULL,now()), -- Quick
  ('70000000-0000-0000-0000-00000000005a','50000000-0000-0000-0000-000000000004',90,NULL,now()); -- Breakfast

INSERT INTO recipe_steps (uuid, recipe_id, order_index, instruction, updated_at, deleted_at, server_updated_at) VALUES
  -- 47 Chicken Tikka Masala
  ('80000000-0000-0000-0000-000000000016','70000000-0000-0000-0000-000000000047',1,'Marinate chicken in yogurt and spices for at least 1 hour',71,NULL,now()),
  ('80000000-0000-0000-0000-000000000017','70000000-0000-0000-0000-000000000047',2,'Sear chicken, then simmer in tomato-cream sauce until cooked through',71,NULL,now()),
  -- 48 Beef Tacos
  ('80000000-0000-0000-0000-000000000018','70000000-0000-0000-0000-000000000048',1,'Brown ground beef with onion, garlic and taco spices',72,NULL,now()),
  ('80000000-0000-0000-0000-000000000019','70000000-0000-0000-0000-000000000048',2,'Fill shells with beef, top with bell pepper and salsa',72,NULL,now()),
  -- 49 Greek Salad
  ('80000000-0000-0000-0000-00000000001a','70000000-0000-0000-0000-000000000049',1,'Chop tomatoes and combine with spinach and cucumber',73,NULL,now()),
  ('80000000-0000-0000-0000-00000000001b','70000000-0000-0000-0000-000000000049',2,'Crumble feta on top and dress with olive oil and oregano',73,NULL,now()),
  -- 4a Tomato Cheese Risotto
  ('80000000-0000-0000-0000-00000000001c','70000000-0000-0000-0000-00000000004a',1,'Toast rice in butter, add warm stock one ladle at a time',74,NULL,now()),
  ('80000000-0000-0000-0000-00000000001d','70000000-0000-0000-0000-00000000004a',2,'Stir in chopped tomato and finish with Parmesan off the heat',74,NULL,now()),
  -- 4b French Onion Soup
  ('80000000-0000-0000-0000-00000000001e','70000000-0000-0000-0000-00000000004b',1,'Slowly caramelise sliced onions in butter over low heat for 40 min',75,NULL,now()),
  ('80000000-0000-0000-0000-00000000001f','70000000-0000-0000-0000-00000000004b',2,'Pour into oven-safe bowls, top with bread and cheese, grill until bubbling',75,NULL,now()),
  -- 4c Chicken Caesar Salad
  ('80000000-0000-0000-0000-000000000020','70000000-0000-0000-0000-00000000004c',1,'Season and grill chicken breast until cooked through, then slice',76,NULL,now()),
  ('80000000-0000-0000-0000-000000000021','70000000-0000-0000-0000-00000000004c',2,'Toss spinach with Caesar dressing, top with chicken and shaved Parmesan',76,NULL,now()),
  -- 4d Spicy Vegetable Curry
  ('80000000-0000-0000-0000-000000000022','70000000-0000-0000-0000-00000000004d',1,'Fry curry paste with garlic and ginger until fragrant',77,NULL,now()),
  ('80000000-0000-0000-0000-000000000023','70000000-0000-0000-0000-00000000004d',2,'Add tomatoes, broccoli and spinach; simmer 20 min in coconut milk',77,NULL,now()),
  -- 4e Salmon with Broccoli
  ('80000000-0000-0000-0000-000000000024','70000000-0000-0000-0000-00000000004e',1,'Season salmon with garlic, lemon and olive oil; bake at 200°C for 15 min',78,NULL,now()),
  ('80000000-0000-0000-0000-000000000025','70000000-0000-0000-0000-00000000004e',2,'Steam broccoli and toss with garlic butter; serve alongside salmon',78,NULL,now()),
  -- 4f Beef Burger Bowl
  ('80000000-0000-0000-0000-000000000026','70000000-0000-0000-0000-00000000004f',1,'Season beef, form into patty and grill 4 min each side',79,NULL,now()),
  ('80000000-0000-0000-0000-000000000027','70000000-0000-0000-0000-00000000004f',2,'Serve over cooked rice with diced tomato and onion',79,NULL,now()),
  -- 50 Egg & Spinach Toast
  ('80000000-0000-0000-0000-000000000028','70000000-0000-0000-0000-000000000050',1,'Wilt spinach in a pan with a pinch of salt for 2 min',80,NULL,now()),
  ('80000000-0000-0000-0000-000000000029','70000000-0000-0000-0000-000000000050',2,'Scramble eggs into the spinach, serve on toast and top with cheese',80,NULL,now()),
  -- 51 Chocolate Mousse
  ('80000000-0000-0000-0000-00000000002a','70000000-0000-0000-0000-000000000051',1,'Melt chocolate with milk over a bain-marie, cool slightly',81,NULL,now()),
  ('80000000-0000-0000-0000-00000000002b','70000000-0000-0000-0000-000000000051',2,'Fold whipped egg whites into chocolate; chill 2 hours, top with almonds',81,NULL,now()),
  -- 52 Fluffy Pancakes
  ('80000000-0000-0000-0000-00000000002c','70000000-0000-0000-0000-000000000052',1,'Whisk flour, eggs and milk into a smooth batter; rest 5 min',82,NULL,now()),
  ('80000000-0000-0000-0000-00000000002d','70000000-0000-0000-0000-000000000052',2,'Cook in a buttered pan over medium heat, 2 min each side until golden',82,NULL,now()),
  -- 53 Lentil Soup
  ('80000000-0000-0000-0000-00000000002e','70000000-0000-0000-0000-000000000053',1,'Sauté onion and garlic until soft; add lentils and stock',83,NULL,now()),
  ('80000000-0000-0000-0000-00000000002f','70000000-0000-0000-0000-000000000053',2,'Stir in tomatoes and simmer 25 min until lentils are tender',83,NULL,now()),
  -- 54 Rice & Veggie Bowl
  ('80000000-0000-0000-0000-000000000030','70000000-0000-0000-0000-000000000054',1,'Cook rice according to package instructions',84,NULL,now()),
  ('80000000-0000-0000-0000-000000000031','70000000-0000-0000-0000-000000000054',2,'Steam broccoli and bell pepper; arrange over rice with soy sauce drizzle',84,NULL,now()),
  -- 55 Chicken Wrap
  ('80000000-0000-0000-0000-000000000032','70000000-0000-0000-0000-000000000055',1,'Season and pan-fry chicken strips until golden, about 8 min',85,NULL,now()),
  ('80000000-0000-0000-0000-000000000033','70000000-0000-0000-0000-000000000055',2,'Layer spinach and tomato in a tortilla with chicken; wrap tightly',85,NULL,now()),
  -- 56 Shakshuka
  ('80000000-0000-0000-0000-000000000034','70000000-0000-0000-0000-000000000056',1,'Sauté bell pepper with spices and garlic; add crushed tomatoes',86,NULL,now()),
  ('80000000-0000-0000-0000-000000000035','70000000-0000-0000-0000-000000000056',2,'Make wells, crack in eggs, cover and cook 10 min until whites are set',86,NULL,now()),
  -- 57 Yogurt Almond Bowl
  ('80000000-0000-0000-0000-000000000036','70000000-0000-0000-0000-000000000057',1,'Spoon yogurt into a bowl',87,NULL,now()),
  ('80000000-0000-0000-0000-000000000037','70000000-0000-0000-0000-000000000057',2,'Top with toasted oats, sliced almonds and a drizzle of honey',87,NULL,now()),
  -- 58 Stuffed Bell Peppers
  ('80000000-0000-0000-0000-000000000038','70000000-0000-0000-0000-000000000058',1,'Mix cooked beef and rice with tomato sauce and spices',88,NULL,now()),
  ('80000000-0000-0000-0000-000000000039','70000000-0000-0000-0000-000000000058',2,'Stuff into halved peppers and bake at 190°C for 25 min until tender',88,NULL,now()),
  -- 59 Garlic Shrimp Stir-Fry
  ('80000000-0000-0000-0000-00000000003a','70000000-0000-0000-0000-000000000059',1,'Heat oil, add minced garlic and shrimp; stir-fry 3 min',89,NULL,now()),
  ('80000000-0000-0000-0000-00000000003b','70000000-0000-0000-0000-000000000059',2,'Add broccoli and soy sauce; toss 2 more min until broccoli is tender-crisp',89,NULL,now()),
  -- 5a Overnight Oats
  ('80000000-0000-0000-0000-00000000003c','70000000-0000-0000-0000-00000000005a',1,'Combine oats, milk, yogurt and a pinch of salt in a jar or bowl',90,NULL,now()),
  ('80000000-0000-0000-0000-00000000003d','70000000-0000-0000-0000-00000000005a',2,'Cover and refrigerate overnight; top with fruit or nuts before serving',90,NULL,now());

-- All sample data inserted.
