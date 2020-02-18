require 'stripe'
require 'sinatra'
require 'dotenv'

# Replace if using a different env file or config
Dotenv.load
Stripe.api_key = ENV['STRIPE_SECRET_KEY']

set :static, true
set :public_folder, File.join(File.dirname(__FILE__), ENV['STATIC_DIR'])
set :port, 4242

# For demo purposes we're hardcoding the amount and currency here.
# Replace this with your cart functionality.
cart = {
  amount: 1099,
  currency: 'AUD'
}

def create_order(items)
  # Replace this with your order creation logic.
  # Calculate the order total on the server to prevent
  # people from directly manipulating the amount on the client.
  items
end

get '/' do
  # Display checkout page
  content_type 'text/html'
  send_file File.join(settings.public_folder, 'index.html')
end

get '/config' do
  content_type 'application/json'
  {
    publicKey: ENV['STRIPE_PUBLISHABLE_KEY'],
    cart: cart
  }.to_json
end

post '/create-payment-intent' do
  content_type 'application/json'
  data = JSON.parse(request.body.read)

  # Create a new customer object so that we can
  # safe the payment method for future usage.
  customer = Stripe::Customer.create({
    name: data['name'],
    email: data['email']
  })

  # Create a PaymentIntent
  order = create_order(cart)
  payment_intent = Stripe::PaymentIntent.create(
    payment_method_types: ['au_becs_debit'],
    setup_future_usage: 'off_session',
    customer: customer[:id],
    amount: order[:amount],
    currency: order[:currency]
  )

  # Send the client secret
  {
    clientSecret: payment_intent['client_secret']
  }.to_json
end

post '/webhook' do
  # Use webhooks to receive information about asynchronous payment events.
  # For more about our webhook events check out https://stripe.com/docs/webhooks.
  webhook_secret = ENV['STRIPE_WEBHOOK_SECRET']
  payload = request.body.read
  if !webhook_secret.empty?
    # Retrieve the event by verifying the signature using the raw body and secret if webhook signing is configured.
    sig_header = request.env['HTTP_STRIPE_SIGNATURE']
    event = nil

    begin
      event = Stripe::Webhook.construct_event(
        payload, sig_header, webhook_secret
      )
    rescue JSON::ParserError => e
      # Invalid payload
      status 400
      return
    rescue Stripe::SignatureVerificationError => e
      # Invalid signature
      puts '⚠️  Webhook signature verification failed.'
      status 400
      return
    end
  else
    data = JSON.parse(payload, symbolize_names: true)
    event = Stripe::Event.construct_from(data)
  end
  # Get the type of webhook event sent - used to check the status of PaymentIntents.
  event_type = event['type']
  data = event['data']
  data_object = data['object']

  if event_type == 'payment_intent.succeeded'
    puts '💰 Payment received!'
    # Fulfill any orders, e-mail receipts, etc
    # To cancel the payment you will need to issue a Refund (https://stripe.com/docs/api/refunds)
  elsif event_type == 'payment_intent.payment_failed'
    puts '❌ Payment failed.'
  end

  content_type 'application/json'
  {
    status: 'success'
  }.to_json
end
