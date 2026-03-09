import { Component, OnInit } from '@angular/core';
import { User } from "src/app/model/user.model";
import { NgForm } from '@angular/forms';
import { LoginService } from 'src/app/services/login/login.service';
import { UserService } from 'src/app/services/user.service';
import { Router } from '@angular/router';
import { getCookie } from 'typescript-cookie';


@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  authStatus: string = "";
  model = new User();

  constructor(private loginService: LoginService, private userService: UserService, private router: Router) {

   }

  ngOnInit(): void {

  }

  validateUser(loginForm: NgForm) {
    console.log('Starting login process...');
    this.loginService.validateLoginDetails(this.model).subscribe(
      responseData => {
        console.log('Login successful, response:', responseData);
        window.sessionStorage.setItem("Authorization",responseData.headers.get('Authorization')!);
        this.model = <any> responseData.body;
        this.model.authStatus = 'AUTH';
        window.sessionStorage.setItem("userdetails",JSON.stringify(this.model));
        let xsrf = getCookie('XSRF-TOKEN')!;
        window.sessionStorage.setItem("XSRF-TOKEN",xsrf);
        
        console.log('Calling user-info API...');
        // Call user-info API to get/create user profile
        this.userService.getUserInfo().subscribe(
          userInfo => {
            console.log('User-info API successful:', userInfo);
            // Update user details with the response from user-info API
            this.model = userInfo;
            this.model.authStatus = 'AUTH';
            window.sessionStorage.setItem("userdetails",JSON.stringify(this.model));
            this.router.navigate(['dashboard']);
          },
          error => {
            console.error('Error fetching user info:', error);
            // Still navigate to dashboard even if user-info fails
            this.router.navigate(['dashboard']);
          }
        );
      },
      error => {
        console.error('Login failed:', error);
      }
    );
  }

}
