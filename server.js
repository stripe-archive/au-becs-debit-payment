const express = require("express");
const app = express();
const { resolve } = require("path");
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
  // people from directly manipulating the amount on the client.
  return items;
};

app.use(express.static("./client/web"));
app.use(
  express.json({
    // We need the raw body to verify webhook signatures.
    // Let's compute it only when hitting the Stripe webhook endpoint.
    verify: function(req, res, buf) {
      if (req.originalUrl.startsWith("/webhook")) {
        req.rawBody = buf.toString();
      }
    }
  })
);

app.get("/config", (req, res) => {
  res.send({
    publicKey: process.env.STRIPE_PUBLISHABLE_KEY,
    cart
  });
});

app.get("/", (req, res) => {
  // Display checkout page
  const path = resolve("./client/web/index.html");
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
app.post("/webhook", async (req, res) => {
  let data, eventType;

  // Check if webhook signing is configured.
  if (process.env.STRIPE_WEBHOOK_SECRET) {
    // Retrieve the event by verifying the signature using the raw body and secret.
    let event;
    let signature = req.headers["stripe-signature"];
    try {
      event = stripe.webhooks.constructEvent(
        req.rawBody,
        signature,
        process.env.STRIPE_WEBHOOK_SECRET
      );
    } catch (err) {
      console.log(`⚠️  Webhook signature verification failed.`);
      return res.sendStatus(400);
    }
    data = event.data;
    eventType = event.type;
  } else {
    // Webhook signing is recommended, but if the secret is not configured in `config.js`,
    // we can retrieve the event data directly from the request body.
    data = req.body.data;
    eventType = req.body.type;
  }

  if (eventType === "payment_intent.succeeded") {
    // Funds have been captured
    // Fulfill any orders, e-mail receipts, etc
    // To cancel the payment you will need to issue a Refund (https://stripe.com/docs/api/refunds)
    console.log("💰 Payment received!");
  } else if (eventType === "payment_intent.payment_failed") {
    console.log("❌ Payment failed.");
  }
  res.sendStatus(200);
});

app.listen(4242, () => console.log(`Node server listening on port ${4242}!`));
