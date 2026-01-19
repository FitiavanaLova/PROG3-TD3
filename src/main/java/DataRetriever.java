import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DataRetriever {
    
    // Méthode pour récupérer un plat par son ID
    public Dish findDishById(Integer id) {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                """
                SELECT id, name, dish_type, selling_price
                FROM dish
                WHERE id = ?;
                """);
            preparedStatement.setInt(1, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                Dish dish = new Dish();
                dish.setId(resultSet.getInt("id"));
                dish.setName(resultSet.getString("name"));
                dish.setDishType(DishTypeEnum.valueOf(resultSet.getString("dish_type")));
                dish.setSellingPrice(resultSet.getObject("selling_price") == null 
                    ? null : resultSet.getDouble("selling_price"));
                dish.setIngredients(findIngredientsByDishId(id));
                return dish;
            }
            dbConnection.closeConnection(connection);
            throw new RuntimeException("Dish not found " + id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    // Nouvelle méthode pour trouver tous les plats
    public List<Dish> findAllDishes() {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        List<Dish> dishes = new ArrayList<>();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                """
                SELECT id, name, dish_type, selling_price
                FROM dish
                ORDER BY id;
                """);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Dish dish = new Dish();
                dish.setId(resultSet.getInt("id"));
                dish.setName(resultSet.getString("name"));
                dish.setDishType(DishTypeEnum.valueOf(resultSet.getString("dish_type")));
                dish.setSellingPrice(resultSet.getObject("selling_price") == null 
                    ? null : resultSet.getDouble("selling_price"));
                dish.setIngredients(findIngredientsByDishId(dish.getId()));
                dishes.add(dish);
            }
            dbConnection.closeConnection(connection);
            return dishes;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    // Méthode pour sauvegarder ou mettre à jour un plat
    public Dish saveDish(Dish toSave) {
        String upsertDishSql = """
            INSERT INTO dish (id, name, dish_type, selling_price)
            VALUES (?, ?, ?::dish_type, ?)
            ON CONFLICT (id) DO UPDATE
            SET name = EXCLUDED.name,
                dish_type = EXCLUDED.dish_type,
                selling_price = EXCLUDED.selling_price
            RETURNING id
        """;

        try (Connection conn = new DBConnection().getConnection()) {
            conn.setAutoCommit(false);
            Integer dishId;
            
            // Sauvegarde du plat
            try (PreparedStatement ps = conn.prepareStatement(upsertDishSql)) {
                if (toSave.getId() != null) {
                    ps.setInt(1, toSave.getId());
                } else {
                    ps.setInt(1, getNextSerialValue(conn, "dish", "id"));
                }
                ps.setString(2, toSave.getName());
                ps.setString(3, toSave.getDishType().name());
                if (toSave.getSellingPrice() != null) {
                    ps.setDouble(4, toSave.getSellingPrice());
                } else {
                    ps.setNull(4, Types.DOUBLE);
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    dishId = rs.getInt(1);
                }
            }

            // Sauvegarde des associations plat-ingrédient
            if (toSave.getIngredients() != null && !toSave.getIngredients().isEmpty()) {
                saveDishIngredients(conn, dishId, toSave.getIngredients());
            } else {
                // Si aucun ingrédient n'est fourni, supprimer toutes les associations
                deleteDishIngredients(conn, dishId);
            }

            conn.commit();
            return findDishById(dishId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    // Méthode pour créer des ingrédients
    public List<Ingredient> createIngredients(List<Ingredient> newIngredients) {
        if (newIngredients == null || newIngredients.isEmpty()) {
            return List.of();
        }
        List<Ingredient> savedIngredients = new ArrayList<>();
        DBConnection dbConnection = new DBConnection();
        Connection conn = dbConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            String insertSql = """
                INSERT INTO ingredient (id, name, category, price)
                VALUES (?, ?, ?::ingredient_category, ?)
                RETURNING id, name, category, price
            """;
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (Ingredient ingredient : newIngredients) {
                    if (ingredient.getId() != null) {
                        ps.setInt(1, ingredient.getId());
                    } else {
                        ps.setInt(1, getNextSerialValue(conn, "ingredient", "id"));
                    }
                    ps.setString(2, ingredient.getName());
                    ps.setString(3, ingredient.getCategory().name());
                    ps.setDouble(4, ingredient.getPrice());

                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        int generatedId = rs.getInt(1);
                        ingredient.setId(generatedId);
                        ingredient.setName(rs.getString("name"));
                        ingredient.setCategory(CategoryEnum.valueOf(rs.getString("category")));
                        ingredient.setPrice(rs.getDouble("price"));
                        savedIngredients.add(ingredient);
                    }
                }
                conn.commit();
                return savedIngredients;
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.closeConnection(conn);
        }
    }
    
    // Méthode pour trouver un ingrédient par son ID
    public Ingredient findIngredientById(Integer id) {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                """
                SELECT id, name, price, category
                FROM ingredient
                WHERE id = ?;
                """);
            preparedStatement.setInt(1, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                Ingredient ingredient = new Ingredient();
                ingredient.setId(resultSet.getInt("id"));
                ingredient.setName(resultSet.getString("name"));
                ingredient.setPrice(resultSet.getDouble("price"));
                ingredient.setCategory(CategoryEnum.valueOf(resultSet.getString("category")));
                dbConnection.closeConnection(connection);
                return ingredient;
            }
            dbConnection.closeConnection(connection);
            throw new RuntimeException("Ingredient not found " + id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    // Méthode pour trouver tous les ingrédients
    public List<Ingredient> findAllIngredients() {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        List<Ingredient> ingredients = new ArrayList<>();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                """
                SELECT id, name, price, category
                FROM ingredient
                ORDER BY id;
                """);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Ingredient ingredient = new Ingredient();
                ingredient.setId(resultSet.getInt("id"));
                ingredient.setName(resultSet.getString("name"));
                ingredient.setPrice(resultSet.getDouble("price"));
                ingredient.setCategory(CategoryEnum.valueOf(resultSet.getString("category")));
                ingredients.add(ingredient);
            }
            dbConnection.closeConnection(connection);
            return ingredients;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    // Méthode pour sauvegarder un ingrédient
    public Ingredient saveIngredient(Ingredient toSave) {
        String upsertSql = """
            INSERT INTO ingredient (id, name, category, price)
            VALUES (?, ?, ?::ingredient_category, ?)
            ON CONFLICT (id) DO UPDATE
            SET name = EXCLUDED.name,
                category = EXCLUDED.category,
                price = EXCLUDED.price
            RETURNING id
        """;

        try (Connection conn = new DBConnection().getConnection()) {
            conn.setAutoCommit(true);
            
            try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                if (toSave.getId() != null) {
                    ps.setInt(1, toSave.getId());
                } else {
                    ps.setInt(1, getNextSerialValue(conn, "ingredient", "id"));
                }
                ps.setString(2, toSave.getName());
                ps.setString(3, toSave.getCategory().name());
                ps.setDouble(4, toSave.getPrice());
                
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    int generatedId = rs.getInt(1);
                    toSave.setId(generatedId);
                }
            }
            
            return findIngredientById(toSave.getId());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    // Méthode pour trouver les plats contenant un ingrédient spécifique
    public List<Dish> findDishesByIngredientId(Integer ingredientId) {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        List<Dish> dishes = new ArrayList<>();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                """
                SELECT d.id, d.name, d.dish_type, d.selling_price
                FROM dish d
                JOIN dish_ingredient di ON d.id = di.id_dish
                WHERE di.id_ingredient = ?
                ORDER BY d.id;
                """);
            preparedStatement.setInt(1, ingredientId);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Dish dish = new Dish();
                dish.setId(resultSet.getInt("id"));
                dish.setName(resultSet.getString("name"));
                dish.setDishType(DishTypeEnum.valueOf(resultSet.getString("dish_type")));
                dish.setSellingPrice(resultSet.getObject("selling_price") == null 
                    ? null : resultSet.getDouble("selling_price"));
                dish.setIngredients(findIngredientsByDishId(dish.getId()));
                dishes.add(dish);
            }
            dbConnection.closeConnection(connection);
            return dishes;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    // Méthode interne pour trouver les ingrédients d'un plat via la table de jointure
    private List<Ingredient> findIngredientsByDishId(Integer dishId) {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        List<Ingredient> ingredients = new ArrayList<>();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                """
                SELECT i.id, i.name, i.price, i.category, 
                       di.quantity_required, di.unit
                FROM ingredient i
                JOIN dish_ingredient di ON i.id = di.id_ingredient
                WHERE di.id_dish = ?
                ORDER BY i.id;
                """);
            preparedStatement.setInt(1, dishId);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Ingredient ingredient = new Ingredient();
                ingredient.setId(resultSet.getInt("id"));
                ingredient.setName(resultSet.getString("name"));
                ingredient.setPrice(resultSet.getDouble("price"));
                ingredient.setCategory(CategoryEnum.valueOf(resultSet.getString("category")));
                ingredient.setQuantity(resultSet.getDouble("quantity_required"));
                ingredient.setUnit(resultSet.getString("unit"));
                ingredients.add(ingredient);
            }
            dbConnection.closeConnection(connection);
            return ingredients;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    // Méthode pour sauvegarder les associations plat-ingrédient
    private void saveDishIngredients(Connection conn, Integer dishId, List<Ingredient> ingredients) 
            throws SQLException {
        // Supprimer les anciennes associations
        deleteDishIngredients(conn, dishId);
        
        // Ajouter les nouvelles associations
        String insertSql = """
            INSERT INTO dish_ingredient (id_dish, id_ingredient, quantity_required, unit)
            VALUES (?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (Ingredient ingredient : ingredients) {
                ps.setInt(1, dishId);
                ps.setInt(2, ingredient.getId());
                ps.setDouble(3, ingredient.getQuantity() != null ? ingredient.getQuantity() : 0.0);
                ps.setString(4, ingredient.getUnit() != null ? ingredient.getUnit() : "UNIT");
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
    
    // Méthode pour supprimer les associations plat-ingrédient
    private void deleteDishIngredients(Connection conn, Integer dishId) throws SQLException {
        String deleteSql = "DELETE FROM dish_ingredient WHERE id_dish = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setInt(1, dishId);
            ps.executeUpdate();
        }
    }
    
    // Méthode pour calculer le coût d'un plat (version améliorée)
    public Double calculateDishCost(Integer dishId) {
        Dish dish = findDishById(dishId);
        return dish.getDishCost();
    }
    
    // Méthode pour calculer la marge brute d'un plat
    public Double calculateGrossMargin(Integer dishId) {
        Dish dish = findDishById(dishId);
        return dish.getGrossMargin();
    }
    
    // Méthodes utilitaires pour la gestion des séquences (inchangées)
    private String getSerialSequenceName(Connection conn, String tableName, String columnName)
            throws SQLException {
        String sql = "SELECT pg_get_serial_sequence(?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    private int getNextSerialValue(Connection conn, String tableName, String columnName)
            throws SQLException {
        String sequenceName = getSerialSequenceName(conn, tableName, columnName);
        if (sequenceName == null) {
            throw new IllegalArgumentException(
                    "Any sequence found for " + tableName + "." + columnName
            );
        }
        updateSequenceNextValue(conn, tableName, columnName, sequenceName);

        String nextValSql = "SELECT nextval(?)";
        try (PreparedStatement ps = conn.prepareStatement(nextValSql)) {
            ps.setString(1, sequenceName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void updateSequenceNextValue(Connection conn, String tableName, String columnName, String sequenceName) 
            throws SQLException {
        String setValSql = String.format(
                "SELECT setval('%s', (SELECT COALESCE(MAX(%s), 0) FROM %s))",
                sequenceName, columnName, tableName
        );
        try (PreparedStatement ps = conn.prepareStatement(setValSql)) {
            ps.executeQuery();
        }
    }
    
    // Méthode pour supprimer un plat et ses associations
    public boolean deleteDish(Integer dishId) {
        try (Connection conn = new DBConnection().getConnection()) {
            conn.setAutoCommit(false);
            
            // Supprimer d'abord les associations dans dish_ingredient
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM dish_ingredient WHERE id_dish = ?")) {
                ps.setInt(1, dishId);
                ps.executeUpdate();
            }
            
            // Supprimer le plat
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM dish WHERE id = ?")) {
                ps.setInt(1, dishId);
                int rowsAffected = ps.executeUpdate();
                conn.commit();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    // Méthode pour supprimer un ingrédient (vérifier qu'il n'est pas utilisé)
    public boolean deleteIngredient(Integer ingredientId) {
        try (Connection conn = new DBConnection().getConnection()) {
            conn.setAutoCommit(false);
            
            // Vérifier si l'ingrédient est utilisé dans des plats
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM dish_ingredient WHERE id_ingredient = ?")) {
                ps.setInt(1, ingredientId);
                ResultSet rs = ps.executeQuery();
                rs.next();
                int count = rs.getInt(1);
                if (count > 0) {
                    throw new RuntimeException(
                        "Cannot delete ingredient with id " + ingredientId + 
                        " because it is used in " + count + " dish(es)"
                    );
                }
            }
            
            // Supprimer l'ingrédient
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ingredient WHERE id = ?")) {
                ps.setInt(1, ingredientId);
                int rowsAffected = ps.executeUpdate();
                conn.commit();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}