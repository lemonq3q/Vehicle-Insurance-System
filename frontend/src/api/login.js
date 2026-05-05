import axios from "./config";
import { buildObjectParams } from "@/utils/params";

const rootUrl = "/auth";
export function login(user){
  return axios.post(`${rootUrl}/login`, user);
}

export function register(user){
  return axios.post(`${rootUrl}/register`, user);
}

export function logout(){
  let url = `${rootUrl}/logout`;
  return axios.get(url);
}

export function forgetPassword(data){
  let url = `${rootUrl}/forget${buildObjectParams(data)}`;
  return axios.get(url);
}

export function getSmsCode(email){
  let url = `${rootUrl}/code?email=${email}`;
  return axios.get(url);
}