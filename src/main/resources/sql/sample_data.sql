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
  ('90000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 'tokhash3', FALSE, NULL, NOW() + INTERVAL '30 days', now()),
  ('90000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004', 'tokhash4', FALSE, NULL, NOW() + INTERVAL '30 days', now()),
  ('90000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000005', 'tokhash5', FALSE, NULL, NOW() + INTERVAL '30 days', now()),
  ('90000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000006', 'tokhash6', FALSE, NULL, NOW() + INTERVAL '30 days', now()),
  ('90000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000007', 'tokhash7', FALSE, NULL, NOW() + INTERVAL '30 days', now()),
  ('90000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000008', 'tokhash8', FALSE, NULL, NOW() + INTERVAL '30 days', now()),
  ('90000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-000000000009', 'tokhash9', FALSE, NULL, NOW() + INTERVAL '30 days', now()),
  ('90000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-00000000000a', 'tokhash10', FALSE, NULL, NOW() + INTERVAL '30 days', now());

-- All sample data inserted.
