package com.privatedrop.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;

import com.privatedrop.dto.EchoRequest;
import com.privatedrop.service.EchoService;

import com.privatedrop.entity.Note;

@RestController
public class HelloController {

    public final EchoService echoService;
    public HelloController(EchoService echoService) {
        this.echoService =  echoService;
    }

    @GetMapping("api/v1/hello")
    public String sayHello(){
        return "Hello from PrivateDrop!!";
    }

//    {
//        "message": "Hello PrivateDrop"
//    }
    @PostMapping("api/v1/echo")
    public String echoMessage(@Valid @RequestBody EchoRequest request){
        return echoService.echo(request.message());
    }

    @PostMapping("api/v1/notes")
    public Note createNote(@RequestBody EchoRequest request){
        return echoService.saveNote(request.message());
    }
}
