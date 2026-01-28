import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        DataRetriever dataRetriever = new DataRetriever();

        try {
            System.out.println("===== TEST INGREDIENTS =====");

            // Création d'ingrédients
            Ingredient tomato = new Ingredient();
            tomato.setName("Tomate");
            tomato.setCategory(CategoryEnum.VEGETABLE);
            tomato.setPrice(500.0);

            Ingredient cheese = new Ingredient();
            cheese.setName("Fromage");
            cheese.setCategory(CategoryEnum.DAIRY);
            cheese.setPrice(1200.0);

            List<Ingredient> ingredientsToCreate = List.of(tomato, cheese);
            List<Ingredient> createdIngredients = dataRetriever.createIngredients(ingredientsToCreate);

            createdIngredients.forEach(i ->
                    System.out.println("Créé : " + i.getId() + " - " + i.getName())
            );

            System.out.println("\n===== TEST FIND ALL INGREDIENTS =====");
            dataRetriever.findAllIngredients().forEach(i ->
                    System.out.println(i.getId() + " | " + i.getName() + " | " + i.getCategory())
            );

            System.out.println("\n===== TEST DISH =====");

            // Préparer un plat
            Dish pizza = new Dish();
            pizza.setName("Pizza Fromage");
            pizza.setDishType(DishTypeEnum.MAIN);
            pizza.setSellingPrice(15000.0);

            // Associer ingrédients au plat
            tomato.setQuantity(2.0);
            tomato.setUnit("PCS");
            cheese.setQuantity(200.0);
            cheese.setUnit("GRAM");

            pizza.setIngredients(List.of(tomato, cheese));

            Dish savedDish = dataRetriever.saveDish(pizza);
            System.out.println("Plat sauvegardé : " + savedDish.getId() + " - " + savedDish.getName());

            System.out.println("\n===== TEST FIND DISH BY ID =====");
            Dish foundDish = dataRetriever.findDishById(savedDish.getId());
            System.out.println(foundDish.getName() + " | " + foundDish.getDishType());
            foundDish.getIngredients().forEach(i ->
                    System.out.println(" - " + i.getName() + " " + i.getQuantity() + " " + i.getUnit())
            );

            System.out.println("\n===== TEST FIND ALL DISHES =====");
            dataRetriever.findAllDishes().forEach(d ->
                    System.out.println(d.getId() + " | " + d.getName())
            );

            System.out.println("\n===== TEST FIND DISHES BY INGREDIENT =====");
            dataRetriever.findDishesByIngredientId(tomato.getId())
                    .forEach(d -> System.out.println(d.getName()));

            System.out.println("\n===== TEST COST & MARGIN =====");
            System.out.println("Coût du plat : " + dataRetriever.calculateDishCost(savedDish.getId()));
            System.out.println("Marge brute : " + dataRetriever.calculateGrossMargin(savedDish.getId()));

            System.out.println("\n===== TEST DELETE DISH =====");
            boolean deleted = dataRetriever.deleteDish(savedDish.getId());
            System.out.println("Plat supprimé ? " + deleted);

            System.out.println("\n===== TEST DELETE INGREDIENT =====");
            for (Ingredient ing : createdIngredients) {
                boolean delIng = dataRetriever.deleteIngredient(ing.getId());
                System.out.println("Ingrédient " + ing.getName() + " supprimé ? " + delIng);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
