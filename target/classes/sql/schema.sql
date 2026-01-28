create type dish_type as enum ('STARTER', 'MAIN', 'DESSERT');


create table dish
(
    id        serial primary key,
    name      varchar(255),
    dish_type dish_type
);

create type ingredient_category as enum ('VEGETABLE', 'ANIMAL', 'MARINE', 'DAIRY', 'OTHER');

create table ingredient
(
    id       serial primary key,
    name     varchar(255),
    price    numeric(10, 2),
    category ingredient_category,
    id_dish  int references dish (id)
);

alter table dish
    add column if not exists price numeric(10, 2);


alter table ingredient
    add column if not exists required_quantity numeric(10, 2);


-- new_schema.sql

-- Ajout de la colonne selling_price (optionnelle) à la table dish
ALTER TABLE dish ADD COLUMN IF NOT EXISTS selling_price NUMERIC(10, 2);

-- Création de la table DishIngredient pour la relation ManyToMany
CREATE TABLE IF NOT EXISTS dish_ingredient (
    id SERIAL PRIMARY KEY,
    id_dish INTEGER NOT NULL REFERENCES dish(id) ON DELETE CASCADE,
    id_ingredient INTEGER NOT NULL REFERENCES ingredient(id) ON DELETE CASCADE,
    quantity_required NUMERIC(10, 2) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    UNIQUE(id_dish, id_ingredient)
);

-- Suppression de l'ancienne colonne id_dish dans ingredient car elle n'est plus nécessaire
ALTER TABLE ingredient DROP COLUMN IF EXISTS id_dish;

-- Suppression de l'ancienne colonne required_quantity dans ingredient
ALTER TABLE ingredient DROP COLUMN IF EXISTS required_quantity;

