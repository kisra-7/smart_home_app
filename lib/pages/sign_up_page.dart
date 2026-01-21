<<<<<<< HEAD

=======
>>>>>>> cc30e20 (fixed gradle problems)
import 'package:alrawi_app/pages/login_page.dart';
import 'package:flutter/material.dart';

class SignUpPage extends StatefulWidget {
  const SignUpPage({super.key});

  @override
  State<SignUpPage> createState() => _SignUpPageState();
}

class _SignUpPageState extends State<SignUpPage> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
<<<<<<< HEAD
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
              TextField(
                decoration: InputDecoration(
                  hintText: 'Username',
                  
                  border: OutlineInputBorder()
                ),
              ), 
              SizedBox(height: 15,),
              TextField(
                decoration: InputDecoration(
                  hintText: 'E-mail',
                  border: OutlineInputBorder()
                ),
              ), 
              SizedBox(height: 15,),  TextField(
                decoration: InputDecoration(
                  hintText: 'Password',
                  border: OutlineInputBorder()
                ),
              ), 
              SizedBox(height: 20,),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [Icon(Icons.check_box_outline_blank,size: 18,),Text('I agree to the '),Text('terms and conditions',style: TextStyle(
                  fontWeight: FontWeight.w900
                ),),],
              ),
              SizedBox(height: 20,),
             Container(
                    padding: EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(15),
                      color: const Color.fromARGB(255, 128, 198, 48)
                    ),
                    child: Text('Sign Up'),
                  ),
                
                SizedBox(height: 120,),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                Text('Already have an account? ',style: TextStyle(
                  fontSize: 15
                ),),GestureDetector(
          onTap: (){
            Navigator.push(context, MaterialPageRoute(builder: (context){
              return LoginPage();
            }));
          },
          
                  child: Text('login',style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 16
                  ),),
                )
              ],)
          ],),
        ),
      )),
    );
  }
}
=======
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 15),
          child: SingleChildScrollView(
            child: Column(
              children: [
                Hero(
                  tag: 'alrawi',
                  // ignore: sized_box_for_whitespace
                  child: Container(
                    width: double.infinity,
                    height: 280,
                    child: Image.asset('assets/images/alrawi.jpeg'),
                  ),
                ),
                TextField(
                  decoration: InputDecoration(
                    hintText: 'Username',

                    border: OutlineInputBorder(),
                  ),
                ),
                SizedBox(height: 15),
                TextField(
                  decoration: InputDecoration(
                    hintText: 'E-mail',
                    border: OutlineInputBorder(),
                  ),
                ),
                SizedBox(height: 15),
                TextField(
                  decoration: InputDecoration(
                    hintText: 'Password',
                    border: OutlineInputBorder(),
                  ),
                ),
                SizedBox(height: 20),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.check_box_outline_blank, size: 18),
                    Text('I agree to the '),
                    Text(
                      'terms and conditions',
                      style: TextStyle(fontWeight: FontWeight.w900),
                    ),
                  ],
                ),
                SizedBox(height: 20),
                Container(
                  padding: EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(15),
                    color: const Color.fromARGB(255, 128, 198, 48),
                  ),
                  child: Text('Sign Up'),
                ),

                SizedBox(height: 120),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      'Already have an account? ',
                      style: TextStyle(fontSize: 15),
                    ),
                    GestureDetector(
                      onTap: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (context) {
                              return LoginPage();
                            },
                          ),
                        );
                      },

                      child: Text(
                        'login',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
>>>>>>> cc30e20 (fixed gradle problems)
