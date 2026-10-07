package br.com.exameperto.identity;

import java.util.List;

record PayoutPage(List<PayoutView> items, long obligations, long divergences, long uncertain) {}
