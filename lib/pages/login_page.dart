import 'package:alrawi_app/pages/home_page.dart';
import 'package:flutter/material.dart';


class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  TextEditingController eController = TextEditingController();
  TextEditingController pController = TextEditingController();
@override
  void dispose() {
    // TODO: implement dispose
    super.dispose();
    eController.dispose();
    pController.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(),
      backgroundColor: Colors.white,
      body: SafeArea(child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20,vertical: 15),
        child: SingleChildScrollView(
          child: Column(children: [
            Hero(
              tag: 'alrawi',
              // ignore: sized_box_for_whitespace
              child: Container(
                width: double.infinity,
                height: 280,
                child: Image.asset('assets/images/alrawi.jpeg'),),
            ),
              
              SizedBox(height: 15,),
              TextField(
                controller: eController,
                decoration: InputDecoration(
                  hintText: 'E-mail',
                  border: OutlineInputBorder()
                ),
              ), 
              SizedBox(height: 15,),  TextField(
                controller: pController,
                decoration: InputDecoration(
                  hintText: 'Password',
                  border: OutlineInputBorder()
                ),
              ), 
              SizedBox(height: 20,),
              
              SizedBox(height: 20,),
              InkWell(
                  onTap: (){
                    if(eController.text=='alrawi@gmail.com' && pController.text == '12345'){
                      Navigator.push(context, MaterialPageRoute(builder:(context){
                      return HomePage();
                    } ));
                    }
                    else{
                      showDialog(context: context, builder: (context){
                        return AlertDialog(
                          content: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                            children: [
                              Icon(Icons.info_outline),
                              Text('Incorrect Email or Password',style: TextStyle(
                                color: Colors.black
                              ),),
                            ],
                          ),
                        );
                      });
                    }
                    
                  },
                  child: Container(
                    padding: EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(15),
                      color: const Color.fromARGB(255, 128, 198, 48)
                    ),
                    child: Text('Login'),
                  ),
                ),
          ],),
        ),
      )),
    );
  }
}