// A reference to Stripe.js
var stripe;

var orderData = {
  items: [{ id: "photo" }]
};

// Disable the button until we have Stripe set up on the page
document.querySelector("button").disabled = true;
document.getElementById("intent-type").addEventListener("change", function(e) {
  document.getElementById("button-text").setAttribute("class", e.target.value);
});

fetch("/config")
  .then(function(result) {
    return result.json();
  })
  .then(function(data) {
    stripe = Stripe(data.publicKey, { betas: ["au_bank_account_beta_2"] });
    // Show formatted price information.
    var price = (data.amount / 100).toFixed(2);
    var numberFormat = new Intl.NumberFormat(["en-US"], {
      style: "currency",
      currency: data.currency,
      currencyDisplay: "symbol"
    });
    document.getElementById("order-amount").innerText = numberFormat.format(
      price
    );
    var { auBankAccount } = setupElements();

    // Handle form submission.
    var form = document.getElementById("payment-form");
    form.addEventListener("submit", function(event) {
      event.preventDefault();
      changeLoadingState(true);
      // Create PaymentIntent or SetupIntent
      var mode = document.getElementById("intent-type").value;
      createIntent({ mode: mode }).then(intent => {
        pay(stripe, auBankAccount, mode, intent.clientSecret);
      });
    });
  });

var createIntent = async function({ mode }) {
  return await fetch(`/create-${mode}-intent`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(orderData)
  }).then(res => res.json());
};

// Set up Stripe.js and Elements to use in checkout form
var setupElements = function() {
  var elements = stripe.elements();
  // Custom styling can be passed to options when creating an Element
  const style = {
    base: {
      // Add your base input styles here. For example:
      fontSize: "16px",
      color: "#32325d"
    }
  };

  const options = {
    style: style,
    disabled: false,
    hideIcon: false,
    iconStyle: "default" // or "solid"
  };

  // Create an instance of the auBankAccount Element.
  const auBankAccount = elements.create("auBankAccount", options);

  // Add an instance of the auBankAccount Element into
  // the `au-bank-account-element` <div>.
  auBankAccount.mount("#au-bank-account-element");

  auBankAccount.on("change", function(event) {
    // Handle real-time validation errors from the Element.
    if (event.error) {
      showError(event.error.message);
    } else if (event.complete) {
      document.getElementById("bank-name").textContent =
        `(${event.bankName})` || "";
      // Enable button.
      document.querySelector("button").disabled = false;
    } else {
      document.querySelector("button").disabled = true;
    }
  });

  return {
    stripe: stripe,
    auBankAccount: auBankAccount
  };
};

/*
 * Calls stripe.confirmAuBecsDebitPayment to generate the mandate and initaite the debit.
 */
var pay = function(stripe, auBankAccount, mode, clientSecret) {
  var confirm = {
    payment: stripe.confirmAuBecsDebitPayment,
    setup: stripe.confirmAuBecsDebitSetup
  };
  confirm[mode](clientSecret, {
    payment_method: {
      au_becs_debit: auBankAccount,
      billing_details: {
        name: document.querySelector('input[name="name"]').value,
        email: document.querySelector('input[name="email"]').value
      }
    }
  }).then(function(result) {
    var { error, paymentIntent, setupIntent } = result;
    if (error) {
      // Show error to your customer
      showError(error.message);
    } else if (paymentIntent) {
      orderComplete(paymentIntent);
    } else if (setupIntent) {
      orderComplete(setupIntent);
    } else {
      showError("An unexpected error occured.");
    }
  });
};

/* ------- Post-payment helpers ------- */

/* Shows a success / error message when the payment is complete */
var orderComplete = function(object) {
  var stringifiedObject = JSON.stringify(object, null, 2);

  document.querySelector(".sr-payment-form").classList.add("hidden");
  document.querySelector("pre").textContent = stringifiedObject;

  document.querySelector(".sr-result").classList.remove("hidden");
  setTimeout(function() {
    document.querySelector(".sr-result").classList.add("expand");
  }, 200);

  changeLoadingState(false);
};

var showError = function(errorMsgText) {
  changeLoadingState(false);
  var errorMsg = document.querySelector("#error-message");
  errorMsg.textContent = errorMsgText;
  setTimeout(function() {
    errorMsg.textContent = "";
  }, 4000);
};

// Show a spinner on payment submission
var changeLoadingState = function(isLoading) {
  if (isLoading) {
    document.querySelector("button").disabled = true;
    document.querySelector("#spinner").classList.remove("hidden");
    document.querySelector("#button-text").classList.add("hidden");
  } else {
    document.querySelector("button").disabled = true;
    document.querySelector("#spinner").classList.add("hidden");
    document.querySelector("#button-text").classList.remove("hidden");
  }
};
