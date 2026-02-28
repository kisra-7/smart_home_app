import 'package:flutter/material.dart';
import 'home_hub_page.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final TextEditingController eController = TextEditingController();
  final TextEditingController pController = TextEditingController();

  @override
  void dispose() {
    eController.dispose();
    pController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Keep your existing login page if you still use it.
    return Scaffold(
      body: Center(
        child: ElevatedButton(
          onPressed: () => Navigator.pushReplacement(
            context,
            MaterialPageRoute(builder: (_) => const HomeHubPage()),
          ),
          child: const Text("Go Home"),
        ),
      ),
    );
  }
}