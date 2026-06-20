package com.meilluer.smartspacer_irctc

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.TapAction
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate
import com.meilluer.smartspacer_irctc.data.TicketRepository
import com.meilluer.smartspacer_irctc.data.TicketRepository.fromStation
import com.meilluer.smartspacer_irctc.data.TicketRepository.toStation

import android.graphics.drawable.Icon as AndroidIcon

class Target: SmartspacerTargetProvider() {
    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        val preferenceManager = com.meilluer.smartspacer_irctc.data.PreferenceManager(context!!)
        if (!preferenceManager.getVisibility()) return emptyList()

        val subtitleText = if (TicketRepository.journeyStarted && TicketRepository.nextStation.isNotEmpty()) {
            "Next: ${TicketRepository.nextStation} • Arr: ${TicketRepository.arrivalTime}"
        } else {
            "${TicketRepository.seatNumber}/${TicketRepository.coachNumber}/${TicketRepository.seatType} • $fromStation ( ${TicketRepository.fromPlatform} ) → ( ${TicketRepository.toPlatform} ) $toStation"
        }

        val seatDisplayText = if (TicketRepository.seatType.isNotEmpty()) "${TicketRepository.coachNumber}, ${TicketRepository.seatNumber} (${TicketRepository.seatType})" else "${TicketRepository.coachNumber}, ${TicketRepository.seatNumber}"

        val targets = mutableListOf<SmartspaceTarget>()
      
            TargetTemplate.Basic(
                id = "IRCTC_ticket",
                componentName = ComponentName(context!!, Target::class.java),
                title = Text("${TicketRepository.trainNumber} / ${TicketRepository.trainName} • ${TicketRepository.departureTime} → ${TicketRepository.arrivalTime}"),
                subtitle = Text(subtitleText),
                icon = Icon(AndroidIcon.createWithResource(context, R.drawable.noun_train_8295307),shouldTint=false),

            ).create()
        )
          if(
        targets.add( TicketRepository.target_visibility_flag==false){
            return  emptyList()
        }
        
        return targets
    }

    override fun getConfig(smartspacerId: String?): Config {
        return Config(
            label = "Smartspacer IRCTC Ticket",
            description = "Dynamically display IRCTC Ticket",
            icon = android.graphics.drawable.Icon.createWithResource(context, R.drawable.noun_train_8295307),
        )
    }

    override fun onDismiss(smartspacerId: String, targetId: String): Boolean {
        val preferenceManager = com.meilluer.smartspacer_irctc.data.PreferenceManager(context!!)
        preferenceManager.saveVisibility(false)
        TicketRepository.target_visibility_flag = false
        notifyChange("IRCTC_ticket")
        return true
    }
}
