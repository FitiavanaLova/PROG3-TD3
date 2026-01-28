import java.io.Serial;
import java.sql.Timestamp;

public class StockMovement {
    private int id;
    private int id_ingredient;
    private Double quantity;
    private Mouvement_TypeEnum mouvement_Type;
    private UnitEnum unit;
    private Timestamp creation_datetime;

    public StockMovement(int id, int id_ingredient, Double quantity, Mouvement_TypeEnum mouvement_Type, UnitEnum unit, Timestamp creation_datetime) {
        this.id = id;
        this.id_ingredient = id_ingredient;
        this.quantity = quantity;
        this.mouvement_Type = mouvement_Type;
        this.unit = unit;
        this.creation_datetime = creation_datetime;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId_ingredient() {
        return id_ingredient;
    }

    public void setId_ingredient(int id_ingredient) {
        this.id_ingredient = id_ingredient;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public Mouvement_TypeEnum getMouvement_Type() {
        return mouvement_Type;
    }

    public void setMouvement_Type(Mouvement_TypeEnum mouvement_Type) {
        this.mouvement_Type = mouvement_Type;
    }

    public UnitEnum getUnit() {
        return unit;
    }

    public void setUnit(UnitEnum unit) {
        this.unit = unit;
    }

    public Timestamp getCreation_datetime() {
        return creation_datetime;
    }

    public void setCreation_datetime(Timestamp creation_datetime) {
        this.creation_datetime = creation_datetime;
    }
}
