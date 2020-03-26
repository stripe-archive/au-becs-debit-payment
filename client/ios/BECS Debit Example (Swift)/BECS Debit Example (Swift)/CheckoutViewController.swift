//
//  CheckoutViewController.swift
//  BECS Debit Example (Swift)
//
//  Created by Cameron Sabol on 3/23/20.
//  Copyright © 2020 Stripe. All rights reserved.
//

import UIKit

import Stripe

/**
 * To run this app, you'll need to first run the sample server locally.
 * Follow the "How to run locally" instructions in the root directory's README.md to get started.
 * Once you've started the server, open http://localhost:4242 in your browser to check that the
 * server is running locally.
 * After verifying the sample server is running locally, build and run the app using the iOS simulator.
 */
let BackendUrl = "http://127.0.0.1:4242/"

class CheckoutViewController: UIViewController {

    private var becsFormView = STPAUBECSDebitFormView(companyName: "Example Company Inc.")
    private let payButton = UIButton()

    private var paymentIntentClientSecret: String?

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = .white

        payButton.layer.cornerRadius = 5
        payButton.contentEdgeInsets = UIEdgeInsets(top: 4, left: 8, bottom: 4, right: 8)
        payButton.backgroundColor = .systemGray3
        payButton.titleLabel?.font = UIFont.systemFont(ofSize: 18)
        payButton.setTitle("Accept Mandate and Pay", for: .normal)
        payButton.addTarget(self, action: #selector(pay), for: .touchUpInside)
        payButton.isEnabled = false
        payButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(payButton)

        addBECSFormView()

        NSLayoutConstraint.activate([
            payButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
        ])
        getPublicKey()
    }

    private func addBECSFormView() {
        becsFormView.becsDebitFormDelegate = self
        becsFormView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(becsFormView)

        NSLayoutConstraint.activate([
                   // the leading and trailing anchors explicitly do not constrain to the
                   // safeAreaLayoutGuide because STPAUBECSDebitFormView automatically
                   // lays out its contents respecting all margins and we want the background
                   // to extend to the screen edges
                   becsFormView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
                   view.trailingAnchor.constraint(equalTo: becsFormView.trailingAnchor),

                   becsFormView.topAnchor.constraint(equalToSystemSpacingBelow: view.safeAreaLayoutGuide.topAnchor, multiplier: 2),

                   payButton.topAnchor.constraint(equalToSystemSpacingBelow: becsFormView.bottomAnchor, multiplier: 2),
               ])
    }

    func displayAlert(title: String, message: String, restartDemo: Bool = false) {
        DispatchQueue.main.async {
            let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
            if restartDemo {
                alert.addAction(UIAlertAction(title: "Restart demo", style: .cancel) { _ in
                    self.becsFormView.removeFromSuperview()
                    self.becsFormView = STPAUBECSDebitFormView(companyName: "Example Company Inc.")
                    self.addBECSFormView()
                    self.startCheckout()
                })
            }
            else {
                alert.addAction(UIAlertAction(title: "OK", style: .cancel))
            }
            self.present(alert, animated: true, completion: nil)
        }
    }

    func getPublicKey() {
        // Create a PaymentIntent by calling the sample server's /create-payment-intent endpoint.
        let url = URL(string: BackendUrl + "config")!
        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        let task = URLSession.shared.dataTask(with: request, completionHandler: { [weak self] (data, response, error) in
            guard let response = response as? HTTPURLResponse,
                response.statusCode == 200,
                let data = data,
                let json = try? JSONSerialization.jsonObject(with: data, options: []) as? [String : Any],
                let stripePublishableKey = json["publishableKey"] as? String else {
                    let message = error?.localizedDescription ?? "Failed to decode response from server."
                    self?.displayAlert(title: "Error loading page", message: message)
                    return
            }
            // Configure the SDK with your Stripe publishable key so that it can make requests to the Stripe API
            Stripe.setDefaultPublishableKey(stripePublishableKey)
            self?.startCheckout()
        })
        task.resume()
    }

    func startCheckout() {
        // Create a PaymentIntent by calling the sample server's /create-payment-intent endpoint.
        let url = URL(string: BackendUrl + "create-payment-intent")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"

        request.addValue("application/json", forHTTPHeaderField: "Content-Type")

        request.httpBody = try? JSONSerialization.data(withJSONObject: ["items": 1, "currency": "eur"], options: [])

        let task = URLSession.shared.dataTask(with: request, completionHandler: { [weak self] (data, response, error) in
            guard let response = response as? HTTPURLResponse,
                response.statusCode == 200,
                let data = data,
                let json = try? JSONSerialization.jsonObject(with: data, options: []) as? [String : Any],
                let clientSecret = json["clientSecret"] as? String else {
                    let message = error?.localizedDescription ?? "Failed to decode response from server."
                    self?.displayAlert(title: "Error loading page", message: message)
                    return
            }
            self?.paymentIntentClientSecret = clientSecret
        })
        task.resume()
    }

    @objc
    func pay() {
        guard let paymentIntentClientSecret = paymentIntentClientSecret,
            let paymentMethodParams = becsFormView.paymentMethodParams else {
                return;
        }

        // TODO : Collect BECS params

        let paymentIntentParams = STPPaymentIntentParams(clientSecret: paymentIntentClientSecret)

        paymentIntentParams.paymentMethodParams = paymentMethodParams

        STPPaymentHandler.shared().confirmPayment(withParams: paymentIntentParams,
                                                  authenticationContext: self)
        { (handlerStatus, paymentIntent, error) in
            switch handlerStatus {
            case .succeeded:
                self.displayAlert(title: "Payment successfully created",
                                  message: error?.localizedDescription ?? "",
                                  restartDemo: true)

            case .canceled:
                self.displayAlert(title: "Canceled",
                                  message: error?.localizedDescription ?? "",
                                  restartDemo: false)

            case .failed:
                self.displayAlert(title: "Payment failed",
                                  message: error?.localizedDescription ?? "",
                                  restartDemo: false)

            @unknown default:
                fatalError()
            }
        }



    }

}

extension CheckoutViewController: STPAuthenticationContext {
    func authenticationPresentingViewController() -> UIViewController {
        return self
    }
}

extension CheckoutViewController: STPAUBECSDebitFormViewDelegate {
    func auBECSDebitForm(_ form: STPAUBECSDebitFormView, didChangeToStateComplete complete: Bool) {
        payButton.isEnabled = complete
        payButton.backgroundColor = complete ? .systemBlue : .systemGray3
    }
}
