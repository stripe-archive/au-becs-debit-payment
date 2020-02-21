package com.stripe.sample;

import java.nio.file.Paths;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.staticFiles;
import static spark.Spark.port;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Customer;
import com.stripe.exception.*;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;

import io.github.cdimascio.dotenv.Dotenv;

public class Server {
    private static Gson gson = new Gson();

    // For demo purposes we're hardcoding the amount and currency here.
    // Replace this with your cart functionality.
    static final Map<String, Object> CART = new HashMap<String, Object>() {{
        put("amount", 1099L);
        put("currency", "AUD");
    }};

    static Map<String, Object> createOrder(Map<String, Object> items) {
        // Replace this with your order creation logic.
        // Calculate the order total on the server to prevent
        // manipulation of the amount on the client.
        return items;
    }

    static class CreateRequestBody {
        @SerializedName("name")
        String name;

        @SerializedName("email")
        String email;

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }
    }

    public static void main(String[] args) {
        port(4242);
        Dotenv dotenv = Dotenv.load();

        Stripe.apiKey = dotenv.get("STRIPE_SECRET_KEY");

        staticFiles.externalLocation(
                Paths.get(Paths.get("").toAbsolutePath().toString(), dotenv.get("STATIC_DIR")).normalize().toString());

        get("/config", (request, response) -> {
            response.type("application/json");

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("publishableKey", dotenv.get("STRIPE_PUBLISHABLE_KEY"));
            Map<String, Object> nestedParams = new HashMap<>();
            nestedParams.put("amount", CART.get("amount"));
            nestedParams.put("currency", CART.get("currency"));
            responseData.put("cart", nestedParams);
            return gson.toJson(responseData);
        });

        post("/create-payment-intent", (request, response) -> {
            response.type("application/json");

            CreateRequestBody postBody = gson.fromJson(request.body(), CreateRequestBody.class);
            // Create a new customer object so that we can
            // safe the payment method for future usage.
            Map<String, Object> params = new HashMap<>();
            params.put("name", postBody.getName());
            params.put("email", postBody.getEmail());

            Customer customer = Customer.create(params);

            Map<String, Object> order = createOrder(CART);
            
            PaymentIntentCreateParams createParams = new PaymentIntentCreateParams.Builder()
                    .addPaymentMethodType("au_becs_debit")
                    .setCurrency((String) order.get("currency"))
                    .setAmount((Long) order.get("amount"))
                    .setCustomer(customer.getId())
                    .setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION)
                    .build();
            // Create a PaymentIntent with the order amount and currency
            PaymentIntent intent = PaymentIntent.create(createParams);
            // Send the client secret to the client
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("clientSecret", intent.getClientSecret());
            return gson.toJson(responseData);
        });

        post("/webhook", (request, response) -> {
            String payload = request.body();
            String sigHeader = request.headers("Stripe-Signature");
            String endpointSecret = dotenv.get("STRIPE_WEBHOOK_SECRET");

            Event event = null;

            try {
                event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            } catch (SignatureVerificationException e) {
                // Invalid signature
                response.status(400);
                return "";
            }

            switch (event.getType()) {
            case "payment_intent.succeeded":
                // Fulfill any orders, e-mail receipts, etc
                // To cancel the payment you will need to issue a Refund
                // (https://stripe.com/docs/api/refunds)
                System.out.println("💰Payment received!");
                break;
            case "payment_intent.payment_failed":
                System.out.println("❌ Payment failed.");
                break;
            }
            
            // Acknowledge receipt of webhook event.
            response.status(200);
            return "";    
        });
    }
}