package com.example.app

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import com.example.app.databinding.CheckoutActivityBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stripe.android.*
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.view.BecsDebitWidget
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class CheckoutActivityKotlin : AppCompatActivity() {
    private val viewBinding: CheckoutActivityBinding by lazy {
        CheckoutActivityBinding.inflate(layoutInflater)
    }

    private val viewModel: CheckoutViewModel by lazy {
        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory(application)
        ).get(CheckoutViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        viewBinding.becsDebitWidget.validParamsCallback =
            object : BecsDebitWidget.ValidParamsCallback {
                override fun onInputChanged(isValid: Boolean) {
                    viewBinding.payButton.isEnabled = isValid
                }
            }

        viewBinding.payButton.setOnClickListener {
            viewBinding.becsDebitWidget.params?.let { params ->
                onPayClicked(params)
            }
        }

        viewBinding.progressBar.visibility = View.VISIBLE
        viewModel.getPublishableKey().observe(this, Observer {
            viewBinding.progressBar.visibility = View.INVISIBLE
            when (it) {
                is CheckoutViewModel.FetchResult.Success -> {
                    onPublishableKeyFetched(it.data.getString("publishableKey"))
                }
                is CheckoutViewModel.FetchResult.Error -> {
                    displayError(it.exception)
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        viewBinding.progressBar.visibility = View.VISIBLE
        viewModel.onPaymentResult(requestCode, data).observe(
            this,
            Observer {
                viewBinding.progressBar.visibility = View.INVISIBLE
                when (it) {
                    is CheckoutViewModel.PaymentResult.Success -> {
                        displayPaymentResult(it.result)
                    }
                    is CheckoutViewModel.PaymentResult.Error -> {
                        displayError(it.exception)
                    }
                }
            }
        )
    }

    private fun onPayClicked(params: PaymentMethodCreateParams) {
        viewModel.stripe?.confirmPayment(
            this,
            ConfirmPaymentIntentParams.createWithPaymentMethodCreateParams(
                clientSecret = requireNotNull(viewModel.clientSecret),
                paymentMethodCreateParams = params
            )
        )
    }

    private fun displayPaymentResult(paymentIntentResult: PaymentIntentResult) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Success")
            .setMessage("Outcome: " + paymentIntentResult.outcome.let {
                when (it) {
                    StripeIntentResult.Outcome.SUCCEEDED -> "success"
                    StripeIntentResult.Outcome.FAILED -> "failed"
                    StripeIntentResult.Outcome.CANCELED -> "canceled"
                    else -> "unknown"
                }
            })
            .setPositiveButton(android.R.string.ok, null)
            .create()
            .show()
    }

    private fun displayError(exception: Exception) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(exception.message)
            .setNeutralButton(android.R.string.ok, null)
            .create()
            .show()
    }

    private fun onPublishableKeyFetched(publishableKey: String) {
        viewModel.publishableKey = publishableKey

        viewBinding.progressBar.visibility = View.VISIBLE
        viewModel.createPaymentIntent(JSONObject()).observe(
            this,
            Observer {
                viewBinding.progressBar.visibility = View.INVISIBLE
                when (it) {
                    is CheckoutViewModel.FetchResult.Success -> {
                        onClientSecretFetched(it.data.getString("clientSecret"))
                    }
                    is CheckoutViewModel.FetchResult.Error -> {
                        displayError(it.exception)
                    }
                }
            }
        )
    }

    private fun onClientSecretFetched(clientSecret: String) {
        viewModel.clientSecret = clientSecret
    }

    internal class CheckoutViewModel(
        application: Application
    ) : AndroidViewModel(application) {
        private val context = application.applicationContext
        private val httpClient = OkHttpClient()

        internal var publishableKey: String? = null
        internal var clientSecret: String? = null

        val stripe: Stripe?
            get() {
                return publishableKey?.let {
                    Stripe(context, it)
                }
            }

        fun getPublishableKey(): LiveData<FetchResult> {
            val liveData = MutableLiveData<FetchResult>()
            val request = Request.Builder()
                .url("$BACKEND_URL/config")
                .get()
                .build()
            httpClient.newCall(request)
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        liveData.postValue(FetchResult.Error(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!response.isSuccessful) {
                            liveData.postValue(
                                FetchResult.Error(IOException("Response unsuccessful"))
                            )
                        } else {
                            val responseData = response.body?.string()
                            val responseJson = responseData?.let {
                                JSONObject(it)
                            } ?: JSONObject()
                            liveData.postValue(FetchResult.Success(responseJson))
                        }
                    }
                })
            return liveData
        }

        fun createPaymentIntent(body: JSONObject): LiveData<FetchResult> {
            val liveData = MutableLiveData<FetchResult>()

            val request = Request.Builder()
                .url("$BACKEND_URL/create-payment-intent")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(MEDIA_TYPE))
                .build()

            httpClient.newCall(request)
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        liveData.postValue(FetchResult.Error(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!response.isSuccessful) {
                            liveData.postValue(
                                FetchResult.Error(IOException("Response unsuccessful"))
                            )
                        } else {
                            val responseData = response.body?.string()
                            val responseJson = responseData?.let {
                                JSONObject(it)
                            } ?: JSONObject()
                            liveData.postValue(FetchResult.Success(responseJson))
                        }
                    }
                })

            return liveData
        }

        fun onPaymentResult(
            requestCode: Int,
            data: Intent?
        ): LiveData<PaymentResult> {
            val liveData = MutableLiveData<PaymentResult>()
            stripe?.let { stripe ->
                if (stripe.isPaymentResult(requestCode, data)) {
                    viewModelScope.launch {
                        liveData.value = runCatching {
                            stripe.getPaymentIntentResult(requestCode, data!!)
                        }.fold(
                            onSuccess = {
                                PaymentResult.Success(it)
                            },
                            onFailure = {
                                PaymentResult.Error(it as Exception)
                            }
                        )
                    }
                }
            }
            return liveData
        }

        internal sealed class FetchResult {
            data class Success(val data: JSONObject) : FetchResult()
            data class Error(val exception: Exception) : FetchResult()
        }

        internal sealed class PaymentResult {
            data class Success(val result: PaymentIntentResult) : PaymentResult()
            data class Error(val exception: Exception) : PaymentResult()
        }

        private companion object {
            private const val BACKEND_URL = "http://10.0.2.2:4242"
            private val MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        }
    }
}