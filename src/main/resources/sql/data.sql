insert into dish (id, name, dish_type)
values (1, 'Salaide fraîche', 'STARTER'),
       (2, 'Poulet grillé', 'MAIN'),
       (3, 'Riz aux légumes', 'MAIN'),
       (4, 'Gâteau au chocolat ', 'DESSERT'),
       (5, 'Salade de fruits', 'DESSERT');


insert into ingredient (id, name, category, price, id_dish)
values (1, 'Laitue', 'VEGETABLE', 800.0, 1),
       (2, 'Tomate', 'VEGETABLE', 600.0, 1),
       (3, 'Poulet', 'ANIMAL', 4500.0, 2),
       (4, 'Chocolat ', 'OTHER', 3000.0, 4),
       (5, 'Beurre', 'DAIRY', 2500.0, 4);



update dish
set price = 2000.0
where id = 1;

update dish
set price = 6000.0
where id = 2;


-- Insertion des données dans dish_ingredient selon le document
INSERT INTO dish_ingredient (id_dish, id_ingredient, quantity_required, unit) VALUES
(1, 1, 0.20, 'KG'),  -- Salade fraîche - Laitue
(1, 2, 0.15, 'KG'),  -- Salade fraîche - Tomate
(2, 3, 1.00, 'KG'),  -- Poulet grillé - Poulet
(4, 4, 0.30, 'KG'),  -- Gâteau au chocolat - Chocolat
(4, 5, 0.20, 'KG');  -- Gâteau au chocolat - Beurre

-- Mise à jour des prix de vente dans dish
UPDATE dish SET selling_price = 3500.00 WHERE id = 1;  -- Salade fraîche
UPDATE dish SET selling_price = 12000.00 WHERE id = 2; -- Poulet grillé
UPDATE dish SET selling_price = 8000.00 WHERE id = 4;  -- Gâteau au chocolat
-- Les plats 3 et 5 gardent selling_price = NULL
