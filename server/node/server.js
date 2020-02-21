const express = require("express");
const app = express();
const { resolve } = require("path");
const bodyParser = require("body-parser");
// Replace if using a different env file or config
require("dotenv").config({ path: "./.env" });
const stripe = require("stripe")(process.env.STRIPE_SECRET_KEY);

// For demo purposes we're hardcoding the amount and currency here.
// Replace this with your cart functionality.
const cart = {
  amount: 1099,
  currency: "AUD"
};

const createOrder = items => {
  // Replace this with your order creation logic.
  // Calculate the order total on the server to prevent
  // manipulation of the amount on the client.
  return items;
};

app.use(express.static(process.env.STATIC_DIR));
// Use JSON parser for all non-webhook routes
app.use((req, res, next) => {
  if (req.originalUrl === "/webhook") {
    next();
  } else {
    bodyParser.json()(req, res, next);
  }
});

app.get("/config", (req, res) => {
  res.send({
    publishableKey: process.env.STRIPE_PUBLISHABLE_KEY,
    cart
  });
});

app.get("/", (req, res) => {
  // Display checkout page
  const path = resolve(process.env.STATIC_DIR + "/index.html");
  res.sendFile(path);
});

app.post("/create-payment-intent", async (req, res) => {
  const { name, email } = req.body;
  // Create a new customer object so that we can
  // safe the payment method for future usage.
  const customer = await stripe.customers.create({
    name,
    email
  });

  const { amount, currency } = createOrder(cart);
  // Create a PaymentIntent with the order amount and currency
  const paymentIntent = await stripe.paymentIntents.create({
    amount,
    currency,
    customer: customer.id,
    setup_future_usage: "off_session",
    payment_method_types: ["au_becs_debit"]
  });

  // Send PaymentIntent client_secret
  res.send({
    clientSecret: paymentIntent.client_secret
  });
});

// Expose a endpoint as a webhook handler for asynchronous events.
// Configure your webhook in the stripe developer dashboard
// https://dashboard.stripe.com/test/webhooks
app.post(
  "/webhook",
  bodyParser.raw({ type: "application/json" }),
  async (req, res) => {
    let event;

    try {
      event = stripe.webhooks.constructEvent(
        req.body,
        req.headers["stripe-signature"],
        process.env.STRIPE_WEBHOOK_SECRET
      );
    } catch (err) {
      // On error, log and return the error message
      console.log(`❌ Error message: ${err.message}`);
      return res.status(400).send(`Webhook Error: ${err.message}`);
    }

    if (event.type === "payment_intent.succeeded") {
      // Funds have been captured
      // Fulfill any orders, e-mail receipts, etc
      // To cancel the payment you will need to issue a Refund (https://stripe.com/docs/api/refunds)
      console.log("💰 Payment received!");
    } else if (event.type === "payment_intent.payment_failed") {
      console.log("❌ Payment failed.");
    }
    res.sendStatus(200);
  }
);

app.listen(4242, () => console.log(`Node server listening on port ${4242}!`));
