import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DataRetriever {

    // ===================== ORDERS =====================
    
    public Order findOrderByReference(String reference) {
        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement("""
                    SELECT id, reference, creation_datetime, order_type, order_status
                    FROM "order"
                    WHERE reference LIKE ?""");
            preparedStatement.setString(1, reference);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                Order order = new Order();
                Integer idOrder = resultSet.getInt("id");
                order.setId(idOrder);
                order.setReference(resultSet.getString("reference"));
                order.setCreationDatetime(resultSet.getTimestamp("creation_datetime").toInstant());
                order.setDishOrderList(findDishOrderByIdOrder(idOrder));
                order.setOrderType(OrderTypeEnum.valueOf(resultSet.getString("order_type")));
                order.setOrderStatus(OrderStatusEnum.valueOf(resultSet.getString("order_status")));
                return order;
            }
            throw new RuntimeException("Order not found with reference " + reference);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Order saveOrder(Order orderToSave) {
        DBConnection dbConnection = new DBConnection();

        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);

            // 0️⃣ Vérifier si la commande existe et est DELIVERED
            if (orderToSave.getId() != null) {
                PreparedStatement checkStatus = conn.prepareStatement(
                        "SELECT order_status FROM \"order\" WHERE id = ?"
                );
                checkStatus.setInt(1, orderToSave.getId());
                ResultSet rsStatus = checkStatus.executeQuery();
                if (rsStatus.next()) {
                    OrderStatusEnum status = OrderStatusEnum.valueOf(rsStatus.getString("order_status"));
                    if (status == OrderStatusEnum.DELIVERED) {
                        throw new RuntimeException("Commande livrée ne peut plus être modifiée !");
                    }
                }
            }

            // 1️⃣ Vérification du stock
            for (DishOrder dishOrder : orderToSave.getDishOrderList()) {
                Dish dish = dishOrder.getDish();
                Integer orderedQuantity = dishOrder.getQuantity();

                for (DishIngredient dishIngredient : dish.getDishIngredients()) {
                    Ingredient ingredient = findIngredientById(
                            dishIngredient.getIngredient().getId()
                    );
                    double requiredQuantity =
                            dishIngredient.getQuantity() * orderedQuantity;

                    StockValue stockValue = ingredient.getStockValueAt(Instant.now());

                    if (stockValue == null || stockValue.getQuantity() < requiredQuantity) {
                        throw new RuntimeException(
                                "Stock insuffisant pour l'ingrédient : " + ingredient.getName()
                        );
                    }
                }
            }

            // 2️⃣ Insertion ou mise à jour de la commande
            String upsertOrderSql = """
                    INSERT INTO "order"(id, reference, creation_datetime, order_type, order_status)
                    VALUES (?, ?, ?, ?::order_type, ?::order_status)
                    ON CONFLICT (id) DO UPDATE 
                    SET reference = EXCLUDED.reference,
                        creation_datetime = EXCLUDED.creation_datetime,
                        order_type = EXCLUDED.order_type,
                        order_status = EXCLUDED.order_status
                    RETURNING id
                    """;

            Integer orderId;
            try (PreparedStatement ps = conn.prepareStatement(upsertOrderSql)) {
                if (orderToSave.getId() != null) {
                    ps.setInt(1, orderToSave.getId());
                } else {
                    ps.setInt(1, getNextSerialValue(conn, "order", "id"));
                }
                ps.setString(2, orderToSave.getReference());
                ps.setTimestamp(3, Timestamp.from(orderToSave.getCreationDatetime()));
                ps.setString(4, orderToSave.getOrderType().name());
                ps.setString(5, orderToSave.getOrderStatus().name());

                ResultSet rs = ps.executeQuery();
                rs.next();
                orderId = rs.getInt(1);
            }

            // 3️⃣ Supprimer les anciens dish_order si mise à jour
            if (orderToSave.getId() != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM dish_order WHERE id_order = ?")) {
                    ps.setInt(1, orderId);
                    ps.executeUpdate();
                }
            }

            // 4️⃣ Insertion des nouveaux dish_order
            String insertDishOrderSql = """
                    INSERT INTO dish_order(id, id_order, id_dish, quantity)
                    VALUES (?, ?, ?, ?)
                    """;

            try (PreparedStatement ps = conn.prepareStatement(insertDishOrderSql)) {
                for (DishOrder dishOrder : orderToSave.getDishOrderList()) {
                    ps.setInt(1, getNextSerialValue(conn, "dish_order", "id"));
                    ps.setInt(2, orderId);
                    ps.setInt(3, dishOrder.getDish().getId());
                    ps.setInt(4, dishOrder.getQuantity());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // 5️⃣ Sortie du stock (OUT) - seulement pour les nouvelles commandes
            if (orderToSave.getId() == null || orderToSave.getOrderStatus() != OrderStatusEnum.DELIVERED) {
                String insertStockMovementSql = """
                        INSERT INTO stock_movement
                        (id, id_ingredient, quantity, type, unit, creation_datetime)
                        VALUES (?, ?, ?, ?::movement_type, ?::unit, ?)
                        """;

                try (PreparedStatement ps = conn.prepareStatement(insertStockMovementSql)) {
                    for (DishOrder dishOrder : orderToSave.getDishOrderList()) {
                        for (DishIngredient di : dishOrder.getDish().getDishIngredients()) {

                            double quantityOut =
                                    di.getQuantity() * dishOrder.getQuantity();

                            ps.setInt(1, getNextSerialValue(conn, "stock_movement", "id"));
                            ps.setInt(2, di.getIngredient().getId());
                            ps.setDouble(3, quantityOut);
                            ps.setString(4, MovementTypeEnum.OUT.name());
                            ps.setString(5, di.getUnit().name());
                            ps.setTimestamp(6, Timestamp.from(Instant.now()));
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
            }

            conn.commit();
            return findOrderByReference(orderToSave.getReference());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===================== INGREDIENTS =====================

    public Ingredient saveIngredient(Ingredient ingredientToSave) {
        String upsertIngredientSql = """
                INSERT INTO ingredient (id, name, price, category)
                VALUES (?, ?, ?, ?::ingredient_category)
                ON CONFLICT (id) DO UPDATE
                SET name = EXCLUDED.name,
                    category = EXCLUDED.category,
                    price = EXCLUDED.price
                RETURNING id
                """;
        try (Connection conn = new DBConnection().getConnection()) {
            conn.setAutoCommit(false);
            Integer ingredientId;
            try (PreparedStatement ps = conn.prepareStatement(upsertIngredientSql)) {
                if (ingredientToSave.getId() != null) {
                    ps.setInt(1, ingredientToSave.getId());
                } else {
                    ps.setInt(1, getNextSerialValue(conn, "ingredient", "id"));
                }
                ps.setString(2, ingredientToSave.getName());
                if (ingredientToSave.getPrice() != null) {
                    ps.setDouble(3, ingredientToSave.getPrice());
                } else {
                    ps.setNull(3, Types.DOUBLE);
                }
                ps.setString(4, ingredientToSave.getCategory().name());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    ingredientId = rs.getInt(1);
                }
            }

            if (ingredientToSave.getId() != null) {
                deleteStockMovements(conn, ingredientId);
            }
            
            if (ingredientToSave.getStockMovementList() != null && !ingredientToSave.getStockMovementList().isEmpty()) {
                insertIngredientStockMovements(conn, ingredientToSave.getStockMovementList(), ingredientId);
            }

            conn.commit();
            return findIngredientById(ingredientId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Ingredient findIngredientById(Integer idIngredient) {
        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT id, name, price, category FROM ingredient WHERE id = ?;");
            preparedStatement.setInt(1, idIngredient);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                CategoryEnum category = CategoryEnum.valueOf(resultSet.getString("category"));
                Double price = resultSet.getDouble("price");
                return new Ingredient(id, name, category, price, findStockMovementsByIngredientId(id));
            }
            throw new RuntimeException("Ingredient not found " + idIngredient);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ===================== DISHES =====================

    public Dish findDishById(Integer idDish) {
        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT id as dish_id, name as dish_name, dish_type, selling_price as dish_price FROM dish WHERE id = ?"
            );
            preparedStatement.setInt(1, idDish);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                Dish dish = new Dish();
                dish.setId(resultSet.getInt("dish_id"));
                dish.setName(resultSet.getString("dish_name"));
                dish.setDishType(DishTypeEnum.valueOf(resultSet.getString("dish_type")));
                dish.setPrice(resultSet.getObject("dish_price") == null ? null : resultSet.getDouble("dish_price"));
                dish.setDishIngredients(findIngredientByDishId(idDish));
                return dish;
            }
            throw new RuntimeException("Dish not found " + idDish);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Dish saveDish(Dish dishToSave) {
        String upsertDishSql = """
                INSERT INTO dish (id, selling_price, name, dish_type)
                VALUES (?, ?, ?, ?::dish_type)
                ON CONFLICT (id) DO UPDATE
                SET name = EXCLUDED.name,
                    dish_type = EXCLUDED.dish_type,
                    selling_price = EXCLUDED.selling_price
                RETURNING id
                """;

        try (Connection conn = new DBConnection().getConnection()) {
            conn.setAutoCommit(false);
            Integer dishId;
            try (PreparedStatement ps = conn.prepareStatement(upsertDishSql)) {
                if (dishToSave.getId() != null) {
                    ps.setInt(1, dishToSave.getId());
                } else {
                    ps.setInt(1, getNextSerialValue(conn, "dish", "id"));
                }
                if (dishToSave.getPrice() != null) {
                    ps.setDouble(2, dishToSave.getPrice());
                } else {
                    ps.setNull(2, Types.DOUBLE);
                }
                ps.setString(3, dishToSave.getName());
                ps.setString(4, dishToSave.getDishType().name());
                ResultSet rs = ps.executeQuery();
                rs.next();
                dishId = rs.getInt(1);
            }

            List<DishIngredient> newDishIngredients = dishToSave.getDishIngredients();
            if (newDishIngredients != null) {
                for (DishIngredient di : newDishIngredients) {
                    di.setDish(dishToSave);
                }
            }

            if (dishToSave.getId() != null) {
                detachIngredients(conn, dishId);
            }
            if (newDishIngredients != null && !newDishIngredients.isEmpty()) {
                attachIngredients(conn, newDishIngredients, dishId);
            }

            conn.commit();
            return findDishById(dishId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ===================== HELPER METHODS =====================

    private List<DishOrder> findDishOrderByIdOrder(Integer idOrder) {
        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.getConnection()) {
            List<DishOrder> dishOrders = new ArrayList<>();
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT id, id_dish, quantity FROM dish_order WHERE id_order = ?");
            preparedStatement.setInt(1, idOrder);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Dish dish = findDishById(resultSet.getInt("id_dish"));
                DishOrder dishOrder = new DishOrder();
                dishOrder.setId(resultSet.getInt("id"));
                dishOrder.setQuantity(resultSet.getInt("quantity"));
                dishOrder.setDish(dish);
                dishOrders.add(dishOrder);
            }
            return dishOrders;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<DishIngredient> findIngredientByDishId(Integer idDish) {
        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.getConnection()) {
            List<DishIngredient> dishIngredients = new ArrayList<>();
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT ingredient.id, ingredient.name, ingredient.price, ingredient.category, di.required_quantity, di.unit " +
                            "FROM ingredient JOIN dish_ingredient di ON di.id_ingredient = ingredient.id " +
                            "WHERE id_dish = ?");
            preparedStatement.setInt(1, idDish);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Ingredient ingredient = new Ingredient();
                ingredient.setId(resultSet.getInt("id"));
                ingredient.setName(resultSet.getString("name"));
                ingredient.setPrice(resultSet.getDouble("price"));
                ingredient.setCategory(CategoryEnum.valueOf(resultSet.getString("category")));

                DishIngredient dishIngredient = new DishIngredient();
                dishIngredient.setIngredient(ingredient);
                dishIngredient.setQuantity(resultSet.getObject("required_quantity") == null ? null : resultSet.getDouble("required_quantity"));
                dishIngredient.setUnit(Unit.valueOf(resultSet.getString("unit")));

                dishIngredients.add(dishIngredient);
            }
            return dishIngredients;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<StockMovement> findStockMovementsByIngredientId(Integer id) {
        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.getConnection()) {
            List<StockMovement> stockMovementList = new ArrayList<>();
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT id, quantity, unit, type, creation_datetime FROM stock_movement WHERE id_ingredient = ?;");
            preparedStatement.setInt(1, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                StockMovement stockMovement = new StockMovement();
                stockMovement.setId(resultSet.getInt("id"));
                stockMovement.setType(MovementTypeEnum.valueOf(resultSet.getString("type")));
                stockMovement.setCreationDatetime(resultSet.getTimestamp("creation_datetime").toInstant());
                StockValue stockValue = new StockValue();
                stockValue.setQuantity(resultSet.getDouble("quantity"));
                stockValue.setUnit(Unit.valueOf(resultSet.getString("unit")));
                stockMovement.setValue(stockValue);
                stockMovementList.add(stockMovement);
            }
            return stockMovementList;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void detachIngredients(Connection conn, Integer dishId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM dish_ingredient WHERE id_dish = ?")) {
            ps.setInt(1, dishId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void attachIngredients(Connection conn, List<DishIngredient> ingredients, Integer dishId) throws SQLException {
        if (ingredients == null || ingredients.isEmpty()) return;
        String attachSql = """
                INSERT INTO dish_ingredient (id, id_ingredient, id_dish, required_quantity, unit)
                VALUES (?, ?, ?, ?, ?::unit)
                """;
        try (PreparedStatement ps = conn.prepareStatement(attachSql)) {
            for (DishIngredient dishIngredient : ingredients) {
                ps.setInt(1, getNextSerialValue(conn, "dish_ingredient", "id"));
                ps.setInt(2, dishIngredient.getIngredient().getId());
                ps.setInt(3, dishId);
                ps.setDouble(4, dishIngredient.getQuantity());
                ps.setString(5, dishIngredient.getUnit().name());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void deleteStockMovements(Connection conn, Integer ingredientId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM stock_movement WHERE id_ingredient = ?")) {
            ps.setInt(1, ingredientId);
            ps.executeUpdate();
        }
    }

    private void insertIngredientStockMovements(Connection conn, List<StockMovement> stockMovementList, Integer ingredientId) {
        if (stockMovementList == null || stockMovementList.isEmpty()) return;
        String sql = """
                INSERT INTO stock_movement(id, id_ingredient, quantity, type, unit, creation_datetime)
                VALUES (?, ?, ?, ?::movement_type, ?::unit, ?)
                """;
        try (PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            for (StockMovement stockMovement : stockMovementList) {
                preparedStatement.setInt(1, getNextSerialValue(conn, "stock_movement", "id"));
                preparedStatement.setInt(2, ingredientId);
                preparedStatement.setDouble(3, stockMovement.getValue().getQuantity());
                preparedStatement.setString(4, stockMovement.getType().name());
                preparedStatement.setString(5, stockMovement.getValue().getUnit().name());
                preparedStatement.setTimestamp(6, Timestamp.from(stockMovement.getCreationDatetime()));
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ===================== SEQUENCE MANAGEMENT =====================

    private String getSerialSequenceName(Connection conn, String tableName, String columnName) throws SQLException {
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

    private int getNextSerialValue(Connection conn, String tableName, String columnName) throws SQLException {
        String sequenceName = getSerialSequenceName(conn, tableName, columnName);
        if (sequenceName == null) {
            throw new IllegalArgumentException(
                    "Aucune séquence trouvée pour " + tableName + "." + columnName
            );
        }
        
        String nextValSql = "SELECT nextval(?)";
        try (PreparedStatement ps = conn.prepareStatement(nextValSql)) {
            ps.setString(1, sequenceName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    throw new SQLException("Impossible d'obtenir la valeur suivante de la séquence: " + sequenceName);
                }
            }
        }
    }

    // Méthode optionnelle pour réinitialiser les séquences
    public void fixSequencesIfNeeded() {
        try (Connection conn = new DBConnection().getConnection()) {
            String[] tables = {"ingredient", "dish", "dish_ingredient", "stock_movement", "dish_order"};
            
            for (String table : tables) {
                try {
                    String sequenceName = getSerialSequenceName(conn, table, "id");
                    if (sequenceName != null) {
                        String fixSql = String.format(
                            "SELECT setval('%s', (SELECT COALESCE(MAX(id), 0) FROM %s) + 1, false)",
                            sequenceName, table
                        );
                        try (PreparedStatement ps = conn.prepareStatement(fixSql)) {
                            ps.executeQuery();
                            System.out.println("✓ Séquence fixée pour table: " + table);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("⚠ Erreur fixation séquence " + table + ": " + e.getMessage());
                }
            }
            
            // Table "order" spéciale
            try {
                String sequenceName = getSerialSequenceName(conn, "order", "id");
                if (sequenceName != null) {
                    String fixSql = String.format(
                        "SELECT setval('%s', (SELECT COALESCE(MAX(id), 0) FROM \"order\") + 1, false)",
                        sequenceName
                    );
                    try (PreparedStatement ps = conn.prepareStatement(fixSql)) {
                        ps.executeQuery();
                        System.out.println("✓ Séquence fixée pour table: order");
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠ Erreur fixation séquence order: " + e.getMessage());
            }
            
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}