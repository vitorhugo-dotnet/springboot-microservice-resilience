package com.vitorhugo.demoui.web;

import com.vitorhugo.demoui.client.ApiCallResult;
import com.vitorhugo.demoui.client.OrdersApiClient;
import com.vitorhugo.demoui.client.PaymentsApiClient;
import com.vitorhugo.payments.mode.PaymentMode;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
public class DemoController {

    private final PaymentsApiClient paymentsApiClient;
    private final OrdersApiClient ordersApiClient;

    public DemoController(PaymentsApiClient paymentsApiClient, OrdersApiClient ordersApiClient) {
        this.paymentsApiClient = paymentsApiClient;
        this.ordersApiClient = ordersApiClient;
    }

    @GetMapping("/")
    public String home(Model model) {
        populatePage(model);
        return "index";
    }

    @PostMapping("/modes/{mode}")
    public String changeMode(@PathVariable String mode, Model model, RedirectAttributes redirectAttributes) {
        try {
            PaymentMode paymentMode = PaymentMode.valueOf(mode.toUpperCase(Locale.ROOT));
            redirectAttributes.addFlashAttribute("modeResult", paymentsApiClient.switchMode(paymentMode));
            return "redirect:/";
        } catch (IllegalArgumentException exception) {
            populatePage(model);
            model.addAttribute("modeResult", invalidModeResult(mode));
            return "index";
        }
    }

    @PostMapping("/orders")
    public String createOrder(@Valid @ModelAttribute("orderForm") CreateOrderForm orderForm,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populatePage(model);
            return "index";
        }

        redirectAttributes.addFlashAttribute("orderResult",
                ordersApiClient.createOrder(orderForm.customerId(), orderForm.amount()));
        return "redirect:/";
    }

    @PostMapping("/resilience/refresh")
    public String refreshResilience(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("resilienceResult", ordersApiClient.resilienceStatus());
        return "redirect:/";
    }

    private void populatePage(Model model) {
        model.addAttribute("modes", PaymentMode.values());
        model.addAttribute("currentModeResult", paymentsApiClient.currentMode());
        if (!model.containsAttribute("orderForm")) {
            model.addAttribute("orderForm", new CreateOrderForm(null, null));
        }
    }

    private ApiCallResult invalidModeResult(String mode) {
        return new ApiCallResult("POST", "/modes/" + mode, null, null,
                "Modo de pagamento invalido: " + mode + ".", false);
    }
}
