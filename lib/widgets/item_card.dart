import 'package:flutter/material.dart';


class ItemCard extends StatelessWidget {
  const ItemCard({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 150,
      height: 100,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        color: Colors.black38
      ),
      child: Center(child: Text('Light switch')),
    );
  }
}