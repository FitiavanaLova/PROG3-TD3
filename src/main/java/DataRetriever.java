import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DataRetriever {

    // =====================================================
    // ================= INGREDIENT ========================
    // =====================================================

    public Ingredient findIngredientById(Integer id) {
        DBConnection dbConnection = new DBConnection();
        Connection conn = dbConnection.getConnection();

        try {
            PreparedStatement ps = conn.prepareStatement(
                """
                SELECT id, name, price, category
                FROM ingredient
                WHERE id = ?
                """
            );
            ps.setInt(1, id);

            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new RuntimeException("Ingredient not found : " + id);
            }

            Ingredient ingredient = new Ingredient();
            ingredient.setId(rs.getInt("id"));
            ingredient.setName(rs.getString("name"));
            ingredient.setPrice(rs.getDouble("price"));
            ingredient.setCategory(CategoryEnum.valueOf(rs.getString("category")));

            ingredient.setStockMovementList(findStockMovementsByIngredientId(id));

            dbConnection.closeConnection(conn);
            return ingredient;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Ingredient> findAllIngredients() {
        DBConnection dbConnection = new DBConnection();
        Connection conn = dbConnection.getConnection();
        List<Ingredient> ingredients = new ArrayList<>();

        try {
            PreparedStatement ps = conn.prepareStatement(
                """
                SELECT id, name, price, category
                FROM ingredient
                ORDER BY id
                """
            );

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Ingredient ingredient = new Ingredient();
                ingredient.setId(rs.getInt("id"));
                ingredient.setName(rs.getString("name"));
                ingredient.setPrice(rs.getDouble("price"));
                ingredient.setCategory(CategoryEnum.valueOf(rs.getString("category")));

                ingredient.setStockMovementList(
                        findStockMovementsByIngredientId(ingredient.getId())
                );

                ingredients.add(ingredient);
            }

            dbConnection.closeConnection(conn);
            return ingredients;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

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
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {

                if (toSave.getId() != null) {
                    ps.setInt(1, toSave.getId());
                } else {
                    ps.setInt(1, getNextSerialValue(conn, "ingredient", "id"));
                }

                ps.setString(2, toSave.getName());
                ps.setString(3, toSave.getCategory().name());
                ps.setDouble(4, toSave.getPrice());

                ResultSet rs = ps.executeQuery();
                rs.next();
                toSave.setId(rs.getInt(1));
            }

            // Sauvegarde des mouvements de stock (ON CONFLICT DO NOTHING)
            if (toSave.getStockMovementList() != null) {
                saveStockMovements(conn, toSave.getId(), toSave.getStockMovementList());
            }

            conn.commit();
            return findIngredientById(toSave.getId());

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // =====================================================
    // =============== STOCK MOVEMENTS =====================
    // =====================================================

    private List<StockMovement> findStockMovementsByIngredientId(Integer ingredientId) {
        DBConnection dbConnection = new DBConnection();
        Connection conn = dbConnection.getConnection();
        List<StockMovement> list = new ArrayList<>();

        try {
            PreparedStatement ps = conn.prepareStatement(
                """
                SELECT id, id_ingredient, quantity, mouvement_type, unit, creation_datetime
                FROM stock_movement
                WHERE id_ingredient = ?
                ORDER BY creation_datetime
                """
            );
            ps.setInt(1, ingredientId);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                StockMovement sm = new StockMovement(
                        rs.getInt("id"),
                        rs.getInt("id_ingredient"),
                        rs.getDouble("quantity"),
                        Mouvement_TypeEnum.valueOf(rs.getString("mouvement_type")),
                        UnitEnum.valueOf(rs.getString("unit")),
                        rs.getTimestamp("creation_datetime")
                );
                list.add(sm);
            }

            dbConnection.closeConnection(conn);
            return list;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveStockMovements(Connection conn, Integer ingredientId,
                                    List<StockMovement> movements) throws SQLException {

        String insertSql = """
            INSERT INTO stock_movement
            (id, id_ingredient, quantity, mouvement_type, unit, creation_datetime)
            VALUES (?, ?, ?, ?::mouvement_type, ?, ?)
            ON CONFLICT (id) DO NOTHING
        """;

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (StockMovement sm : movements) {
                ps.setInt(1, sm.getId());
                ps.setInt(2, ingredientId);
                ps.setDouble(3, sm.getQuantity());
                ps.setString(4, sm.getMouvement_Type().name());
                ps.setString(5, sm.getUnit().name());
                ps.setTimestamp(6, sm.getCreation_datetime());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // =====================================================
    // ================= UTILITAIRES =======================
    // =====================================================

    private String getSerialSequenceName(Connection conn, String table, String column)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT pg_get_serial_sequence(?, ?)")) {
            ps.setString(1, table);
            ps.setString(2, column);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getString(1);
        }
    }

    private int getNextSerialValue(Connection conn, String table, String column)
            throws SQLException {

        String seq = getSerialSequenceName(conn, table, column);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT setval(?, (SELECT COALESCE(MAX(" + column + "), 0) FROM " + table + "))")) {
            ps.setString(1, seq);
            ps.execute();
        }

        try (PreparedStatement ps = conn.prepareStatement("SELECT nextval(?)")) {
            ps.setString(1, seq);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }
}
