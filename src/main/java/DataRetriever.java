import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DataRetriever {

    // ===================== PUSH-DOWN PROCESSING =====================

    public StockValue getStockValueAt(Instant t, Integer ingredientIdentifier) {

        String sql = """
                SELECT unit,
                       SUM(
                           CASE
                               WHEN type = 'OUT' THEN -quantity
                               ELSE quantity
                           END
                       ) AS actual_quantity
                FROM stock_movement
                WHERE id_ingredient = ?
                  AND creation_datetime <= ?
                GROUP BY unit
                """;

        try (Connection connection = new DBConnection().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, ingredientIdentifier);
            ps.setTimestamp(2, Timestamp.from(t));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StockValue stockValue = new StockValue();
                    stockValue.setUnit(Unit.valueOf(rs.getString("unit")));
                    stockValue.setQuantity(rs.getDouble("actual_quantity"));
                    return stockValue;
                }
                return null;
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    // ===================== STOCK STATISTICS (DATABASE-SIDE PROCESSING) =====================

public void getStockStatistics(
        String periodicity,   // DAY, WEEK, MONTH
        Instant intervalMin,
        Instant intervalMax
) {

    String sql = """
        SELECT 
            i.name AS ingredient_name,
            DATE_TRUNC(?, sm.creation_datetime) AS period,
            SUM(
                CASE 
                    WHEN sm.type = 'OUT' THEN -sm.quantity
                    ELSE sm.quantity
                END
            ) AS total_quantity
        FROM stock_movement sm
        JOIN ingredient i ON i.id = sm.id_ingredient
        WHERE sm.creation_datetime BETWEEN ? AND ?
        GROUP BY i.name, period
        ORDER BY i.name, period
        """;

    try (Connection connection = new DBConnection().getConnection();
         PreparedStatement ps = connection.prepareStatement(sql)) {

        // day / week / month en minuscule pour PostgreSQL
        ps.setString(1, periodicity.toLowerCase());
        ps.setTimestamp(2, Timestamp.from(intervalMin));
        ps.setTimestamp(3, Timestamp.from(intervalMax));

        try (ResultSet rs = ps.executeQuery()) {

            System.out.println("===== STOCK STATISTICS =====");

            while (rs.next()) {

                String ingredientName = rs.getString("ingredient_name");
                Instant period = rs.getTimestamp("period").toInstant();
                double quantity = rs.getDouble("total_quantity");

                System.out.println(
                        ingredientName + " | " +
                        period + " | " +
                        quantity
                );
            }
        }

    } catch (SQLException e) {
        throw new RuntimeException(e);
    }
}


    // ===================== DISH COST =====================

    public Double getDishCost(Integer dishId) {

        String sql = """
                SELECT SUM(i.price * di.required_quantity) AS cost
                FROM dish_ingredient di
                JOIN ingredient i ON i.id = di.id_ingredient
                WHERE di.id_dish = ?
                """;

        try (Connection connection = new DBConnection().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, dishId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject("cost") == null
                            ? null
                            : rs.getDouble("cost");
                }
                return null;
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ===================== GROSS MARGIN =====================

    public Double getGrossMargin(Integer dishId) {

        String sql = """
                SELECT d.selling_price -
                       SUM(i.price * di.required_quantity) AS gross_margin
                FROM dish d
                LEFT JOIN dish_ingredient di ON di.id_dish = d.id
                LEFT JOIN ingredient i ON i.id = di.id_ingredient
                WHERE d.id = ?
                GROUP BY d.selling_price
                """;

        try (Connection connection = new DBConnection().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, dishId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject("gross_margin") == null
                            ? null
                            : rs.getDouble("gross_margin");
                }
                throw new RuntimeException("Dish not found " + dishId);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ===================== FIND INGREDIENT =====================

    public Ingredient findIngredientById(Integer idIngredient) {

        String sql = """
                SELECT id, name, price, category
                FROM ingredient
                WHERE id = ?
                """;

        try (Connection connection = new DBConnection().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, idIngredient);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return new Ingredient(
                            rs.getInt("id"),
                            rs.getString("name"),
                            CategoryEnum.valueOf(rs.getString("category")),
                            rs.getObject("price") == null
                                    ? null
                                    : rs.getDouble("price"),
                            findStockMovementsByIngredientId(idIngredient)
                    );
                }

                throw new RuntimeException("Ingredient not found " + idIngredient);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ===================== STOCK MOVEMENTS =====================

    private List<StockMovement> findStockMovementsByIngredientId(Integer id) {

        String sql = """
                SELECT id, quantity, unit, type, creation_datetime
                FROM stock_movement
                WHERE id_ingredient = ?
                """;

        List<StockMovement> list = new ArrayList<>();

        try (Connection connection = new DBConnection().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    StockMovement sm = new StockMovement();
                    sm.setId(rs.getInt("id"));
                    sm.setType(MovementTypeEnum.valueOf(rs.getString("type")));
                    sm.setCreationDatetime(rs.getTimestamp("creation_datetime").toInstant());

                    StockValue sv = new StockValue();
                    sv.setQuantity(rs.getDouble("quantity"));
                    sv.setUnit(Unit.valueOf(rs.getString("unit")));

                    sm.setValue(sv);

                    list.add(sm);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return list;
    }
}
