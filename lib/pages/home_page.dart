import 'package:alrawi_app/widgets/item_card.dart';
import 'package:flutter/material.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading:  
            Icon(Icons.home),
            title: Text('Alrawi office'),
           centerTitle: false,
           actions: [Padding(
             padding: const EdgeInsets.all(8.0),
             child: Icon(Icons.add),
           )],
        ),
        body: Column(
          children: [

         ItemCard()
           
          ],
        ),
        bottomNavigationBar: BottomNavigationBar(items: [BottomNavigationBarItem(icon: Icon(Icons.home_outlined),label: 'Home'),BottomNavigationBarItem(icon: Icon(Icons.check_box_outlined),label: 'Smart'),BottomNavigationBarItem(icon: Icon(Icons.person_pin),label: 'Me')]),
          );
        
    
  }
}